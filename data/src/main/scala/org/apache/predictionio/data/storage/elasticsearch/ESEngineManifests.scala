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

import scala.collection.JavaConverters._

import org.apache.http.entity.ContentType
import org.apache.http.nio.entity.NStringEntity
import org.apache.http.util.EntityUtils
import org.apache.predictionio.data.storage.EngineManifest
import org.apache.predictionio.data.storage.EngineManifestSerializer
import org.apache.predictionio.data.storage.EngineManifests
import org.apache.predictionio.data.storage.StorageClientConfig
import org.elasticsearch.client.RestClient
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write

import grizzled.slf4j.Logging
import org.elasticsearch.client.ResponseException

class ESEngineManifests(client: ESClient, config: StorageClientConfig, index: String)
    extends EngineManifests with Logging {
  implicit val formats = DefaultFormats + new EngineManifestSerializer
  private val estype = "engine_manifests"
  private def esid(id: String, version: String) = s"$id-$version"

  def insert(engineManifest: EngineManifest): Unit = {
    val id = esid(engineManifest.id, engineManifest.version)
    val restClient = client.open()
    try {
      val entity = new NStringEntity(write(engineManifest), ContentType.APPLICATION_JSON)
      val response = restClient.performRequest(
        "POST",
        s"/$index/$estype/$id",
        Map.empty[String, String].asJava,
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
    } finally {
      restClient.close()
    }
  }

  def get(id: String, version: String): Option[EngineManifest] = {
    val esId = esid(id, version)
    val restClient = client.open()
    try {
      val response = restClient.performRequest(
        "GET",
        s"/$index/$estype/$esId",
        Map.empty[String, String].asJava)
      val jsonResponse = parse(EntityUtils.toString(response.getEntity))
      (jsonResponse \ "found").extract[Boolean] match {
        case true =>
          Some((jsonResponse \ "_source").extract[EngineManifest])
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
        error(s"Failed to access to /$index/$estype/$esId", e)
        None
    } finally {
      restClient.close()
    }
  }

  def getAll(): Seq[EngineManifest] = {
    val restClient = client.open()
    try {
      val json =
        ("query" ->
          ("match_all" ->  List.empty))
      ESUtils.getAll[EngineManifest](restClient, index, estype, compact(render(json)))
    } catch {
      case e: IOException =>
        error("Failed to access to /$index/$estype/_search", e)
        Nil
    } finally {
      restClient.close()
    }
  }

  def update(engineManifest: EngineManifest, upsert: Boolean = false): Unit =
    insert(engineManifest)

  def delete(id: String, version: String): Unit = {
    val esId = esid(id, version)
    val restClient = client.open()
    try {
      val response = restClient.performRequest(
        "DELETE",
        s"/$index/$estype/$esId",
        Map.empty[String, String].asJava)
      val jsonResponse = parse(EntityUtils.toString(response.getEntity))
      val result = (jsonResponse \ "result").extract[String]
      result match {
        case "deleted" =>
        case _ =>
          error(s"[$result] Failed to update $index/$estype/$esId")
      }
    } catch {
      case e: IOException =>
        error(s"Failed to update $index/$estype/$esId", e)
    } finally {
      restClient.close()
    }
  }
}
