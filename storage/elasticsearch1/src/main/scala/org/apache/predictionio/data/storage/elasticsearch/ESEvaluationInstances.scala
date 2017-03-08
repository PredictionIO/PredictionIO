/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.predictionio.data.storage.elasticsearch

import grizzled.slf4j.Logging
import org.apache.predictionio.data.storage.EvaluationInstance
import org.apache.predictionio.data.storage.EvaluationInstanceSerializer
import org.apache.predictionio.data.storage.EvaluationInstances
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

class ESEvaluationInstances(client: Client, config: StorageClientConfig, index: String)
  extends EvaluationInstances with Logging {
  implicit val formats = DefaultFormats + new EvaluationInstanceSerializer
  private val estype = "evaluation_instances"

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
          ("evaluationClass" ->
            ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
          ("engineParamsGeneratorClass" ->
            ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
          ("batch" ->
            ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
          ("evaluatorResults" ->
            ("type" -> "string") ~ ("index" -> "no")) ~
          ("evaluatorResultsHTML" ->
            ("type" -> "string") ~ ("index" -> "no")) ~
          ("evaluatorResultsJSON" ->
            ("type" -> "string") ~ ("index" -> "no"))))
    indices.preparePutMapping(index).setType(estype).
      setSource(compact(render(json))).get
  }

  def insert(i: EvaluationInstance): String = {
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

  def get(id: String): Option[EvaluationInstance] = {
    try {
      val response = client.prepareGet(index, estype, id).get
      if (response.isExists) {
        Some(read[EvaluationInstance](response.getSourceAsString))
      } else {
        None
      }
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        None
    }
  }

  def getAll(): Seq[EvaluationInstance] = {
    try {
      val builder = client.prepareSearch(index).setTypes(estype)
      ESUtils.getAll[EvaluationInstance](client, builder)
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        Seq()
    }
  }

  def getCompleted(): Seq[EvaluationInstance] = {
    try {
      val builder = client.prepareSearch(index).setTypes(estype).setPostFilter(
        termFilter("status", "EVALCOMPLETED")).
        addSort("startTime", SortOrder.DESC)
      ESUtils.getAll[EvaluationInstance](client, builder)
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        Seq()
    }
  }

  def update(i: EvaluationInstance): Unit = {
    try {
      client.prepareUpdate(index, estype, i.id).setDoc(write(i)).get
    } catch {
      case e: ElasticsearchException => error(e.getMessage)
    }
  }

  def delete(id: String): Unit = {
    try {
      client.prepareDelete(index, estype, id).get
    } catch {
      case e: ElasticsearchException => error(e.getMessage)
    }
  }
}
