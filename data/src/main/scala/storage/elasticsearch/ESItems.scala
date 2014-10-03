/** Copyright 2014 TappingStone, Inc.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package io.prediction.data.storage.elasticsearch

import com.github.nscala_time.time.Imports._
import grizzled.slf4j.Logging
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.client.Client
import org.elasticsearch.common.unit.DistanceUnit
import org.elasticsearch.index.query.FilterBuilders._
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.sort.SortBuilders._
import org.elasticsearch.search.sort.SortOrder
import org.joda.time.format.ISODateTimeFormat
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{ read, write }

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration._

import io.prediction.data.storage.{ Item, Items, ItemSerializer, Utils }

/** Elasticsearch implementation of Items. */
class ESItems(client: Client, index: String) extends Items with Logging {
  implicit val formats = DefaultFormats.lossless + new ItemSerializer
  private val estype = "items"
  private val indices = client.admin.indices

  val indexExistResponse = indices.prepareExists(index).get
  if (!indexExistResponse.isExists) {
    indices.prepareCreate(index).get
  }

  val typeExistResponse = indices.prepareTypesExists(index).setTypes(estype).get
  if (!typeExistResponse.isExists) {
    val json = (estype ->
      ("properties" -> ("latlng" -> ("type" -> "geo_point"))))
    indices.preparePutMapping(index).setType(estype).
      setSource(compact(render(json))).get
  }

  def insert(item: Item) = {
    val response = client.prepareIndex(
      index,
      estype,
      Utils.idWithAppid(item.appid, item.id)).setSource(write(item)).get()
  }

  def get(appid: Int, id: String) = {
    try {
      val response = client.prepareGet(
        index,
        estype,
        Utils.idWithAppid(appid, id)).get()
      Some(read[Item](response.getSourceAsString))
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        None
      case e: NullPointerException => None
    }
  }

  def getByAppid(appid: Int) = {
    try {
      val builder = client.prepareSearch(index).setTypes(estype).
        setPostFilter(termFilter("appid", appid))
      ESUtils.getAll[Item](client, builder).toIterator
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        Seq[Item]().toIterator
    }
  }

  def getByAppidAndLatlng(
      appid: Int,
      latlng: Tuple2[Double, Double],
      within: Option[Double],
      unit: Option[String]) = {
    try {
      val filters = Seq(
        Some(termFilter("appid", appid)),
        within map { distance =>
          geoDistanceFilter("latlng").point(latlng._1, latlng._2).distance(
            distance, unit.map {
              case "km" => DistanceUnit.KILOMETERS
              case "mi" => DistanceUnit.MILES
            }.getOrElse(DistanceUnit.KILOMETERS))
        }).flatten
      val builder = client.prepareSearch(index).setTypes(estype).
        setPostFilter(andFilter(filters: _*)).
        addSort(geoDistanceSort("latlng").point(latlng._1, latlng._2))
      ESUtils.getAll[Item](client, builder).toIterator
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        Seq[Item]().toIterator
    }
  }

  def getByAppidAndItypes(appid: Int, itypes: Seq[String]) = {
    try {
      val builder = client.prepareSearch(index).setTypes(estype).setPostFilter(
        andFilter(
          termFilter("appid", appid),
          inFilter("itypes", itypes: _*)))
      ESUtils.getAll[Item](client, builder).toIterator
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        Seq[Item]().toIterator
    }
  }

  def getByAppidAndItypesAndTime(
      appid: Int,
      optItypes: Option[Seq[String]] = None,
      optTime: Option[DateTime] = None): Iterator[Item] = {
    try {
      val filters = Seq(
        Some(termFilter("appid", appid)),
        optItypes map { inFilter("itypes", _: _*) },
        optTime map { s =>
          andFilter(
            rangeFilter("starttime").lt(s),
            orFilter(
              missingFilter("endtime"),
              rangeFilter("endtime").gt(s)))
        }).flatten
      val builder = client.prepareSearch(index).setTypes(estype).setPostFilter(
        andFilter(filters: _*))
      ESUtils.getAll[Item](client, builder).toIterator
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        Seq[Item]().toIterator
    }
  }

  def getByIds(appid: Int, ids: Seq[String]) = {
    try {
      val response = client.prepareMultiGet.add(
        index, estype, ids.map(id => Utils.idWithAppid(appid, id))).get
      response.getResponses.map { mgir =>
        read[Item](mgir.getResponse.getSourceAsString)
      }
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        Seq[Item]()
    }
  }

  def getRecentByIds(appid: Int, ids: Seq[String]) = {
    try {
      val builder = client.prepareSearch(index).setPostFilter(
        idsFilter(estype).ids(ids.map(id => Utils.idWithAppid(appid, id)): _*)).
        addSort("starttime", SortOrder.DESC)
      ESUtils.getAll[Item](client, builder)
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        Seq[Item]()
    }
  }

  def update(item: Item) = insert(item)

  def delete(appid: Int, id: String) = {
    try {
      client.prepareDelete(index, estype, Utils.idWithAppid(appid, id)).get
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
    }
  }

  def delete(item: Item) = delete(item.appid, item.id)

  def deleteByAppid(appid: Int) = {
    try {
      client.prepareDeleteByQuery(index).setTypes(estype).setQuery(
        QueryBuilders.termQuery("appid", appid)).get
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
    }
  }

  def countByAppid(appid: Int): Long = {
    try {
      val response = client.prepareCount(index).setTypes(estype).setQuery(
        QueryBuilders.termQuery("appid", appid)).get
      response.getCount
    } catch {
        case e: ElasticsearchException =>
          error(e.getMessage)
          0
    }
  }
}
