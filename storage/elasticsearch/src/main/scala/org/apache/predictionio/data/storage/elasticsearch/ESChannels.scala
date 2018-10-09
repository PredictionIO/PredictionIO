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
import org.apache.predictionio.data.storage.Channel
import org.apache.predictionio.data.storage.Channels
import org.apache.predictionio.data.storage.StorageClientConfig
import org.elasticsearch.client.{ResponseException, RestClient}
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write

import grizzled.slf4j.Logging

class ESChannels(client: RestClient, config: StorageClientConfig, index: String)
    extends Channels with Logging {
  implicit val formats = DefaultFormats.lossless
  private val estype = "channels"
  private val seq = new ESSequences(client, config, internalIndex)
  private val internalIndex = index + "_" + estype

  ESUtils.createIndex(client, internalIndex,
    ESUtils.getNumberOfShards(config, internalIndex.toUpperCase),
    ESUtils.getNumberOfReplicas(config, internalIndex.toUpperCase))
  val mappingJson =
    (estype ->
      ("_all" -> ("enabled" -> false)) ~
      ("properties" ->
        ("name" -> ("type" -> "keyword"))))
  ESUtils.createMapping(client, internalIndex, estype, compact(render(mappingJson)))

  def insert(channel: Channel): Option[Int] = {
    val id = channel.id match {
      case v if v == 0 =>
        @scala.annotation.tailrec
        def generateId: Int = {
          seq.genNext(estype).toInt match {
            case x if !get(x).isEmpty => generateId
            case x => x
          }
        }
        generateId
      case v => v
    }

    if (update(channel.copy(id = id))) Some(id) else None
  }

  def get(id: Int): Option[Channel] = {
    try {
      val response = client.performRequest(
        "GET",
        s"/$internalIndex/$estype/$id",
        Map.empty[String, String].asJava)
      val jsonResponse = parse(EntityUtils.toString(response.getEntity))
      (jsonResponse \ "found").extract[Boolean] match {
        case true =>
          Some((jsonResponse \ "_source").extract[Channel])
        case _ =>
          None
      }
    } catch {
      case e: ResponseException =>
        e.getResponse.getStatusLine.getStatusCode match {
          case 404 => None
          case _ =>
            error(s"Failed to access to /$internalIndex/$estype/$id", e)
            None
        }
      case e: IOException =>
        error(s"Failed to access to /$internalIndex/$estype/$id", e)
        None
    }
  }

  def getByAppid(appid: Int): Seq[Channel] = {
    try {
      val json =
        ("query" ->
          ("term" ->
            ("appid" -> appid)))
      ESUtils.getAll[Channel](client, internalIndex, estype, compact(render(json)))
    } catch {
      case e: IOException =>
        error(s"Failed to access to /$internalIndex/$estype/_search", e)
        Nil
    }
  }

  def update(channel: Channel): Boolean = {
    val id = channel.id.toString
    try {
      val entity = new NStringEntity(write(channel), ContentType.APPLICATION_JSON)
      val response = client.performRequest(
        "POST",
        s"/$internalIndex/$estype/$id",
        Map("refresh" -> "true").asJava,
        entity)
      val json = parse(EntityUtils.toString(response.getEntity))
      val result = (json \ "result").extract[String]
      result match {
        case "created" => true
        case "updated" => true
        case _ =>
          error(s"[$result] Failed to update $internalIndex/$estype/$id")
          false
      }
    } catch {
      case e: IOException =>
        error(s"Failed to update $internalIndex/$estype/$id", e)
        false
    }
  }

  def delete(id: Int): Unit = {
    try {
      val response = client.performRequest(
        "DELETE",
        s"/$internalIndex/$estype/$id",
        Map("refresh" -> "true").asJava)
      val jsonResponse = parse(EntityUtils.toString(response.getEntity))
      val result = (jsonResponse \ "result").extract[String]
      result match {
        case "deleted" =>
        case _ =>
          error(s"[$result] Failed to update $internalIndex/$estype/$id")
      }
    } catch {
      case e: IOException =>
        error(s"Failed to update $internalIndex/$estype/$id", e)
    }
  }
}
