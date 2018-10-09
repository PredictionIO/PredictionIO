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

import org.apache.http.Header
import org.apache.http.entity.ContentType
import org.apache.http.nio.entity.NStringEntity
import org.apache.http.util.EntityUtils
import org.apache.predictionio.data.storage.StorageClientConfig
import org.apache.predictionio.data.storage.StorageClientException
import org.elasticsearch.client.RestClient
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write

import grizzled.slf4j.Logging

class ESSequences(client: RestClient, config: StorageClientConfig, index: String) extends Logging {
  implicit val formats = DefaultFormats
  private val estype = "sequences"
  private val internalIndex = index + "_" + estype

  ESUtils.createIndex(client, internalIndex,
    ESUtils.getNumberOfShards(config, internalIndex.toUpperCase),
    ESUtils.getNumberOfReplicas(config, internalIndex.toUpperCase))
  val mappingJson =
    (estype ->
      ("_all" -> ("enabled" -> false)) ~
      ("properties" ->
        ("n" -> ("enabled" -> false))))
  ESUtils.createMapping(client, internalIndex, estype, compact(render(mappingJson)))

  def genNext(name: String): Long = {
    try {
      val entity = new NStringEntity(write("n" -> name), ContentType.APPLICATION_JSON)
      val response = client.performRequest(
        "POST",
        s"/$internalIndex/$estype/$name",
        Map("refresh" -> "false").asJava,
        entity)
      val jsonResponse = parse(EntityUtils.toString(response.getEntity))
      val result = (jsonResponse \ "result").extract[String]
      result match {
        case "created" =>
          (jsonResponse \ "_version").extract[Long]
        case "updated" =>
          (jsonResponse \ "_version").extract[Long]
        case _ =>
          throw new IllegalStateException(s"[$result] Failed to update $internalIndex/$estype/$name")
      }
    } catch {
      case e: IOException =>
        throw new StorageClientException(s"Failed to update $internalIndex/$estype/$name", e)
    }
  }
}
