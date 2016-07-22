/** Copyright 2015 TappingStone, Inc.
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

package org.apache.predictionio.data.storage.elasticsearch

import grizzled.slf4j.Logging
import org.apache.predictionio.data.storage.StorageClientConfig
import org.apache.predictionio.data.storage.App
import org.apache.predictionio.data.storage.Apps
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.FilterBuilders._
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.read
import org.json4s.native.Serialization.write

/** Elasticsearch implementation of Items. */
class ESApps(client: Client, config: StorageClientConfig, index: String)
  extends Apps with Logging {
  implicit val formats = DefaultFormats.lossless
  private val estype = "apps"
  private val seq = new ESSequences(client, config, index)

  val indices = client.admin.indices
  val indexExistResponse = indices.prepareExists(index).get
  if (!indexExistResponse.isExists) {
    indices.prepareCreate(index).get
  }
  val typeExistResponse = indices.prepareTypesExists(index).setTypes(estype).get
  if (!typeExistResponse.isExists) {
    val json =
      (estype ->
        ("properties" ->
          ("name" -> ("type" -> "string") ~ ("index" -> "not_analyzed"))))
    indices.preparePutMapping(index).setType(estype).
      setSource(compact(render(json))).get
  }

  def insert(app: App): Option[Int] = {
    val id =
      if (app.id == 0) {
        var roll = seq.genNext("apps")
        while (!get(roll).isEmpty) roll = seq.genNext("apps")
        roll
      }
      else app.id
    val realapp = app.copy(id = id)
    update(realapp)
    Some(id)
  }

  def get(id: Int): Option[App] = {
    try {
      val response = client.prepareGet(
        index,
        estype,
        id.toString).get()
      Some(read[App](response.getSourceAsString))
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        None
      case e: NullPointerException => None
    }
  }

  def getByName(name: String): Option[App] = {
    try {
      val response = client.prepareSearch(index).setTypes(estype).
        setPostFilter(termFilter("name", name)).get
      val hits = response.getHits().hits()
      if (hits.size > 0) {
        Some(read[App](hits.head.getSourceAsString))
      } else {
        None
      }
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        None
    }
  }

  def getAll(): Seq[App] = {
    try {
      val builder = client.prepareSearch(index).setTypes(estype)
      ESUtils.getAll[App](client, builder)
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        Seq[App]()
    }
  }

  def update(app: App): Unit = {
    try {
      val response = client.prepareIndex(index, estype, app.id.toString).
        setSource(write(app)).get()
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
    }
  }

  def delete(id: Int): Unit = {
    try {
      client.prepareDelete(index, estype, id.toString).get
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
    }
  }
}
