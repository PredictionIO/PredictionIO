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
import org.apache.predictionio.data.storage.AccessKey
import org.apache.predictionio.data.storage.AccessKeys
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.FilterBuilders._
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.read
import org.json4s.native.Serialization.write

import scala.util.Random

/** Elasticsearch implementation of AccessKeys. */
class ESAccessKeys(client: Client, config: StorageClientConfig, index: String)
    extends AccessKeys with Logging {
  implicit val formats = DefaultFormats.lossless
  private val estype = "accesskeys"

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
          ("key" -> ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
          ("events" -> ("type" -> "string") ~ ("index" -> "not_analyzed"))))
    indices.preparePutMapping(index).setType(estype).
      setSource(compact(render(json))).get
  }

  def insert(accessKey: AccessKey): Option[String] = {
    val key = if (accessKey.key.isEmpty) generateKey else accessKey.key
    update(accessKey.copy(key = key))
    Some(key)
  }

  def get(key: String): Option[AccessKey] = {
    try {
      val response = client.prepareGet(
        index,
        estype,
        key).get()
      Some(read[AccessKey](response.getSourceAsString))
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        None
      case e: NullPointerException => None
    }
  }

  def getAll(): Seq[AccessKey] = {
    try {
      val builder = client.prepareSearch(index).setTypes(estype)
      ESUtils.getAll[AccessKey](client, builder)
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        Seq[AccessKey]()
    }
  }

  def getByAppid(appid: Int): Seq[AccessKey] = {
    try {
      val builder = client.prepareSearch(index).setTypes(estype).
        setPostFilter(termFilter("appid", appid))
      ESUtils.getAll[AccessKey](client, builder)
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        Seq[AccessKey]()
    }
  }

  def update(accessKey: AccessKey): Unit = {
    try {
      client.prepareIndex(index, estype, accessKey.key).setSource(write(accessKey)).get()
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
    }
  }

  def delete(key: String): Unit = {
    try {
      client.prepareDelete(index, estype, key).get
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
    }
  }
}
