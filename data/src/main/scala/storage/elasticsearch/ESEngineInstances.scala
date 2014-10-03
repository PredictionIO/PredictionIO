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

import io.prediction.data.storage.EngineInstance
import io.prediction.data.storage.EngineInstances
import io.prediction.data.storage.EngineInstanceSerializer

import com.github.nscala_time.time.Imports._
import com.google.common.io.BaseEncoding
import grizzled.slf4j.Logging
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.FilterBuilders._
import org.elasticsearch.search.sort.SortBuilders._
import org.elasticsearch.search.sort.SortOrder
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.{ read, write }

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration._

class ESEngineInstances(client: Client, index: String)
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
          ("engineFactory" ->
            ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
          ("metricsClass" ->
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
          ("metricsParams" ->
            ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
          ("status" -> ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
          ("multipleMetricsResults" ->
            ("type" -> "string") ~ ("index" -> "no")) ~
          ("multipleMetricsResultsHTML" ->
            ("type" -> "string") ~ ("index" -> "no")) ~
          ("multipleMetricsResultsJSON" ->
            ("type" -> "string") ~ ("index" -> "no"))))
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

  def get(id: String) = {
    try {
      val response = client.prepareGet(index, estype, id).get
      if (response.isExists)
        Some(read[EngineInstance](response.getSourceAsString))
      else
        None
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        None
    }
  }

  def getLatestCompleted(engineId: String, engineVersion: String) = {
    try {
      val response = client.prepareSearch(index).setTypes(estype).setPostFilter(
        andFilter(
          termFilter("status", "COMPLETED"),
          termFilter("engineId", engineId),
          termFilter("engineVersion", engineVersion))).
        addSort("startTime", SortOrder.DESC).get
      val hits = response.getHits().hits()
      if (hits.size > 0) {
        Some(read[EngineInstance](hits.head.getSourceAsString))
      } else None
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        None
    }
  }

  def getEvalCompleted() = {
    try {
      val builder = client.prepareSearch(index).setTypes(estype).setPostFilter(
        termFilter("status", "EVALCOMPLETED")).
        addSort("startTime", SortOrder.DESC)
      ESUtils.getAll[EngineInstance](client, builder)
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        Seq()
    }
  }

  def update(i: EngineInstance): Unit = {
    try {
      client.prepareUpdate(index, estype, i.id).setDoc(write(i)).get
    } catch {
      case e: ElasticsearchException => error(e.getMessage)
    }
  }

  def delete(id: String) = {
    try {
      val response = client.prepareDelete(index, estype, id).get
    } catch {
      case e: ElasticsearchException => error(e.getMessage)
    }
  }
}
