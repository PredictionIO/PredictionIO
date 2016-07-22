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
import org.apache.predictionio.data.storage.EngineInstance
import org.apache.predictionio.data.storage.EngineInstanceSerializer
import org.apache.predictionio.data.storage.EngineInstances
import org.apache.predictionio.data.storage.StorageClientConfig
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.FilterBuilders._
import org.elasticsearch.search.sort.SortOrder
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.read
import org.json4s.native.Serialization.write

class ESEngineInstances(client: Client, config: StorageClientConfig, index: String)
  extends EngineInstances with Logging {
  implicit val formats = DefaultFormats + new EngineInstanceSerializer
  private val estype = "engine_instances"

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
          ("status" -> ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
          ("startTime" -> ("type" -> "date")) ~
          ("endTime" -> ("type" -> "date")) ~
          ("engineId" -> ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
          ("engineVersion" ->
            ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
          ("engineVariant" ->
            ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
          ("engineFactory" ->
            ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
          ("batch" ->
            ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
          ("dataSourceParams" ->
            ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
          ("preparatorParams" ->
            ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
          ("algorithmsParams" ->
            ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
          ("servingParams" ->
            ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
          ("status" -> ("type" -> "string") ~ ("index" -> "not_analyzed"))))
    indices.preparePutMapping(index).setType(estype).
      setSource(compact(render(json))).get
  }

  def insert(i: EngineInstance): String = {
    try {
      val response = client.prepareIndex(index, estype).
        setSource(write(i)).get
      response.getId
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        ""
    }
  }

  def get(id: String): Option[EngineInstance] = {
    try {
      val response = client.prepareGet(index, estype, id).get
      if (response.isExists) {
        Some(read[EngineInstance](response.getSourceAsString))
      } else {
        None
      }
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        None
    }
  }

  def getAll(): Seq[EngineInstance] = {
    try {
      val builder = client.prepareSearch(index).setTypes(estype)
      ESUtils.getAll[EngineInstance](client, builder)
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        Seq()
    }
  }

  def getCompleted(
      engineId: String,
      engineVersion: String,
      engineVariant: String): Seq[EngineInstance] = {
    try {
      val builder = client.prepareSearch(index).setTypes(estype).setPostFilter(
        andFilter(
          termFilter("status", "COMPLETED"),
          termFilter("engineId", engineId),
          termFilter("engineVersion", engineVersion),
          termFilter("engineVariant", engineVariant))).
        addSort("startTime", SortOrder.DESC)
      ESUtils.getAll[EngineInstance](client, builder)
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        Seq()
    }
  }

  def getLatestCompleted(
      engineId: String,
      engineVersion: String,
      engineVariant: String): Option[EngineInstance] =
    getCompleted(
      engineId,
      engineVersion,
      engineVariant).headOption

  def update(i: EngineInstance): Unit = {
    try {
      client.prepareUpdate(index, estype, i.id).setDoc(write(i)).get
    } catch {
      case e: ElasticsearchException => error(e.getMessage)
    }
  }

  def delete(id: String): Unit = {
    try {
      val response = client.prepareDelete(index, estype, id).get
    } catch {
      case e: ElasticsearchException => error(e.getMessage)
    }
  }
}
