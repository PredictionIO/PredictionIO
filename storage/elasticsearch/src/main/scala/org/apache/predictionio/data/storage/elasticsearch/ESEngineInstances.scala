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

import java.io.IOException

import scala.collection.JavaConverters.mapAsJavaMapConverter

import org.apache.http.entity.ContentType
import org.apache.http.nio.entity.NStringEntity
import org.apache.http.util.EntityUtils
import org.apache.predictionio.data.storage.EngineInstance
import org.apache.predictionio.data.storage.EngineInstanceSerializer
import org.apache.predictionio.data.storage.EngineInstances
import org.apache.predictionio.data.storage.StorageClientConfig
import org.elasticsearch.client.{ResponseException, RestClient}
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write

import grizzled.slf4j.Logging

class ESEngineInstances(client: RestClient, config: StorageClientConfig, index: String)
    extends EngineInstances with Logging {
  implicit val formats = DefaultFormats + new EngineInstanceSerializer
  private val estype = "engine_instances"
  
  ESUtils.createIndex(client, index,
    ESUtils.getNumberOfShards(config, index.toUpperCase),
    ESUtils.getNumberOfReplicas(config, index.toUpperCase))
  val mappingJson =
    (estype ->
      ("_all" -> ("enabled" -> false)) ~
      ("properties" ->
        ("status" -> ("type" -> "keyword")) ~
        ("startTime" -> ("type" -> "date")) ~
        ("endTime" -> ("type" -> "date")) ~
        ("engineId" -> ("type" -> "keyword")) ~
        ("engineVersion" -> ("type" -> "keyword")) ~
        ("engineVariant" -> ("type" -> "keyword")) ~
        ("engineFactory" -> ("type" -> "keyword")) ~
        ("batch" -> ("type" -> "keyword")) ~
        ("dataSourceParams" -> ("type" -> "keyword")) ~
        ("preparatorParams" -> ("type" -> "keyword")) ~
        ("algorithmsParams" -> ("type" -> "keyword")) ~
        ("servingParams" -> ("type" -> "keyword"))
        ))
  ESUtils.createMapping(client, index, estype, compact(render(mappingJson)))

  def insert(i: EngineInstance): String = {
    val id = i.id match {
      case x if x.isEmpty =>
        @scala.annotation.tailrec
        def generateId(newId: Option[String]): String = {
          newId match {
            case Some(x) => x
            case _ => generateId(preInsert())
          }
        }
        generateId(preInsert())
      case x => x
    }

    update(i.copy(id = id))
    id
  }

  def preInsert(): Option[String] = {
    try {
      val entity = new NStringEntity("{}", ContentType.APPLICATION_JSON)
      val response = client.performRequest(
        "POST",
        s"/$index/$estype/",
        Map("refresh" -> "true").asJava,
        entity)
      val jsonResponse = parse(EntityUtils.toString(response.getEntity))
      val result = (jsonResponse \ "result").extract[String]
      result match {
        case "created" =>
          Some((jsonResponse \ "_id").extract[String])
        case _ =>
          error(s"[$result] Failed to create $index/$estype")
          None
      }
    } catch {
      case e: IOException =>
        error(s"Failed to create $index/$estype", e)
        None
    }
  }

  def get(id: String): Option[EngineInstance] = {
    try {
      val response = client.performRequest(
        "GET",
        s"/$index/$estype/$id",
        Map.empty[String, String].asJava)
      val jsonResponse = parse(EntityUtils.toString(response.getEntity))
      (jsonResponse \ "found").extract[Boolean] match {
        case true =>
          Some((jsonResponse \ "_source").extract[EngineInstance])
        case _ =>
          None
      }
    } catch {
      case e: ResponseException =>
        e.getResponse.getStatusLine.getStatusCode match {
          case 404 => None
          case _ =>
            error(s"Failed to access to /$index/$estype/$id", e)
            None
        }
      case e: IOException =>
        error(s"Failed to access to /$index/$estype/$id", e)
        None
    }
  }

  def getAll(): Seq[EngineInstance] = {
    try {
      val json =
        ("query" ->
          ("match_all" -> List.empty))
      ESUtils.getAll[EngineInstance](client, index, estype, compact(render(json)))
    } catch {
      case e: IOException =>
        error("Failed to access to /$index/$estype/_search", e)
        Nil
    }
  }

  def getCompleted(
    engineId: String,
    engineVersion: String,
    engineVariant: String): Seq[EngineInstance] = {
    try {
      val json =
        ("query" ->
          ("bool" ->
            ("must" -> List(
              ("term" ->
                ("status" -> "COMPLETED")),
              ("term" ->
                ("engineId" -> engineId)),
              ("term" ->
                ("engineVersion" -> engineVersion)),
              ("term" ->
                ("engineVariant" -> engineVariant)))))) ~
              ("sort" -> List(
                ("startTime" ->
                  ("order" -> "desc"))))
      ESUtils.getAll[EngineInstance](client, index, estype, compact(render(json)))
    } catch {
      case e: IOException =>
        error(s"Failed to access to /$index/$estype/_search", e)
        Nil
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
    val id = i.id
    try {
      val entity = new NStringEntity(write(i), ContentType.APPLICATION_JSON)
      val response = client.performRequest(
        "POST",
        s"/$index/$estype/$id",
        Map("refresh" -> "true").asJava,
        entity)
      val jsonResponse = parse(EntityUtils.toString(response.getEntity))
      val result = (jsonResponse \ "result").extract[String]
      result match {
        case "created" =>
        case "updated" =>
        case _ =>
          error(s"[$result] Failed to update $index/$estype/$id")
      }
    } catch {
      case e: IOException =>
        error(s"Failed to update $index/$estype/$id", e)
    }
  }

  def delete(id: String): Unit = {
    try {
      val response = client.performRequest(
        "DELETE",
        s"/$index/$estype/$id",
        Map("refresh" -> "true").asJava)
      val json = parse(EntityUtils.toString(response.getEntity))
      val result = (json \ "result").extract[String]
      result match {
        case "deleted" =>
        case _ =>
          error(s"[$result] Failed to update $index/$estype/$id")
      }
    } catch {
      case e: IOException =>
        error(s"Failed to update $index/$estype/$id", e)
    }
  }
}
