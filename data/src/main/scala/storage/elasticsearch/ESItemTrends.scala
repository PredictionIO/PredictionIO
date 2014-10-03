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

import io.prediction.data.storage.{ ItemTrend, ItemTrends, ItemTrendSerializer, Utils }

/** Elasticsearch implementation of ItemTrends. */
class ESItemTrends(client: Client, index: String) extends ItemTrends with Logging {
  implicit val formats = DefaultFormats.lossless + new ItemTrendSerializer
  private val estype = "itemtrends"

  def insert(itemtrend: ItemTrend) = {
    val response = client.prepareIndex(
      index,
      estype,
      Utils.idWithAppid(itemtrend.appid, itemtrend.id)).
      setSource(write(itemtrend)).get
  }

  def get(appid: Int, id: String) = {
    try {
      val response = client.prepareGet(
        index,
        estype,
        Utils.idWithAppid(appid, id)).get()
      Some(read[ItemTrend](response.getSourceAsString))
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        None
      case e: NullPointerException => None
    }
  }

  def contains(appid: Int, id: String) = !get(appid, id).isEmpty

  def getByAppid(appid: Int) = {
    try {
      val builder = client.prepareSearch(index).setTypes(estype).
        setPostFilter(termFilter("appid", appid))
      ESUtils.getAll[ItemTrend](client, builder).toIterator
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        Seq[ItemTrend]().toIterator
    }
  }

  def getByIds(appid: Int, ids: Seq[String]) = {
    try {
      val response = client.prepareMultiGet.add(
        index, estype, ids.map(id => Utils.idWithAppid(appid, id))).get
      response.getResponses.map { mgir =>
        read[ItemTrend](mgir.getResponse.getSourceAsString)
      }
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        Seq[ItemTrend]()
    }
  }

  def delete(appid: Int, id: String) = {
    try {
      client.prepareDelete(index, estype, Utils.idWithAppid(appid, id)).get
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
    }
  }

  def delete(itemtrend: ItemTrend) = delete(itemtrend.appid, itemtrend.id)

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
