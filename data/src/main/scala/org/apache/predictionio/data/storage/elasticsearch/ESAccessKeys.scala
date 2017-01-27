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
import org.apache.predictionio.data.storage.AccessKey
import org.apache.predictionio.data.storage.AccessKeys
import org.apache.predictionio.data.storage.StorageClientConfig
import org.elasticsearch.client.RestClient
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write

import grizzled.slf4j.Logging
import org.elasticsearch.client.ResponseException

/** Elasticsearch implementation of AccessKeys. */
class ESAccessKeys(client: ESClient, config: StorageClientConfig, index: String)
    extends AccessKeys with Logging {
  implicit val formats = DefaultFormats.lossless
  private val estype = "accesskeys"

  val restClient = client.open()
  try {
    ESUtils.createIndex(restClient, index)
    val mappingJson =
      (estype ->
        ("_all" -> ("enabled" -> 0)) ~
        ("properties" ->
          ("key" -> ("type" -> "keyword")) ~
          ("events" -> ("type" -> "keyword"))))
    ESUtils.createMapping(restClient, index, estype, compact(render(mappingJson)))
  } finally {
    restClient.close()
  }

  def insert(accessKey: AccessKey): Option[String] = {
    val key = if (accessKey.key.isEmpty) generateKey else accessKey.key
    update(accessKey.copy(key = key))
    Some(key)
  }

  def get(id: String): Option[AccessKey] = {
    val restClient = client.open()
    try {
      val response = restClient.performRequest(
        "GET",
        s"/$index/$estype/$id",
        Map.empty[String, String].asJava)
      val jsonResponse = parse(EntityUtils.toString(response.getEntity))
      (jsonResponse \ "found").extract[Boolean] match {
        case true =>
          Some((jsonResponse \ "_source").extract[AccessKey])
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
        error("Failed to access to /$index/$estype/$key", e)
        None
    } finally {
      restClient.close()
    }
  }

  def getAll(): Seq[AccessKey] = {
    val restClient = client.open()
    try {
      val json =
        ("query" ->
          ("match_all" -> List.empty))
      ESUtils.getAll[AccessKey](restClient, index, estype, compact(render(json)))
    } catch {
      case e: IOException =>
        error("Failed to access to /$index/$estype/_search", e)
        Nil
    } finally {
      restClient.close()
    }
  }

  def getByAppid(appid: Int): Seq[AccessKey] = {
    val restClient = client.open()
    try {
      val json =
        ("query" ->
          ("term" ->
            ("appid" -> appid)))
      ESUtils.getAll[AccessKey](restClient, index, estype, compact(render(json)))
    } catch {
      case e: IOException =>
        error("Failed to access to /$index/$estype/_search", e)
        Nil
    } finally {
      restClient.close()
    }
  }

  def update(accessKey: AccessKey): Unit = {
    val id = accessKey.key
    val restClient = client.open()
    try {
      val entity = new NStringEntity(write(accessKey), ContentType.APPLICATION_JSON)
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

  def delete(id: String): Unit = {
    val restClient = client.open()
    try {
      val response = restClient.performRequest(
        "DELETE",
        s"/$index/$estype/$id",
        Map.empty[String, String].asJava)
      val json = parse(EntityUtils.toString(response.getEntity))
      val result = (json \ "result").extract[String]
      result match {
        case "deleted" =>
        case _ =>
          error(s"[$result] Failed to update $index/$estype/id")
      }
    } catch {
      case e: IOException =>
        error(s"Failed to update $index/$estype/id", e)
    } finally {
      restClient.close()
    }
  }
}
