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
import org.apache.predictionio.data.storage.Channel
import org.apache.predictionio.data.storage.Channels
import org.apache.predictionio.data.storage.StorageClientConfig
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.FilterBuilders.termFilter
import org.json4s.DefaultFormats
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.read
import org.json4s.native.Serialization.write

class ESChannels(client: Client, config: StorageClientConfig, index: String)
    extends Channels with Logging {

  implicit val formats = DefaultFormats.lossless
  private val estype = "channels"
  private val seq = new ESSequences(client, config, index)
  private val seqName = "channels"

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

  def insert(channel: Channel): Option[Int] = {
    val id =
      if (channel.id == 0) {
        var roll = seq.genNext(seqName)
        while (!get(roll).isEmpty) roll = seq.genNext(seqName)
        roll
      } else channel.id

    val realChannel = channel.copy(id = id)
    if (update(realChannel)) Some(id) else None
  }

  def get(id: Int): Option[Channel] = {
    try {
      val response = client.prepareGet(
        index,
        estype,
        id.toString).get()
      Some(read[Channel](response.getSourceAsString))
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        None
      case e: NullPointerException => None
    }
  }

  def getByAppid(appid: Int): Seq[Channel] = {
    try {
      val builder = client.prepareSearch(index).setTypes(estype).
        setPostFilter(termFilter("appid", appid))
      ESUtils.getAll[Channel](client, builder)
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        Seq[Channel]()
    }
  }

  def update(channel: Channel): Boolean = {
    try {
      val response = client.prepareIndex(index, estype, channel.id.toString).
        setSource(write(channel)).get()
      true
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        false
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
