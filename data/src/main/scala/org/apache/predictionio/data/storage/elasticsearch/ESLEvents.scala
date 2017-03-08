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
import org.apache.predictionio.data.storage.StorageClientConfig
import org.apache.predictionio.data.storage.App
import org.apache.predictionio.data.storage.Apps
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.FilterBuilders._
import org.joda.time.DateTime
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.read
import org.json4s.native.Serialization.write

import grizzled.slf4j.Logging
import org.apache.predictionio.data.storage.{Event, LEvents, StorageClientConfig}
import org.elasticsearch.client.Client
import org.json4s.DefaultFormats

import scala.concurrent.{Future, ExecutionContext}

class ESLEvents(val client: Client, config: StorageClientConfig, val index: String)
  extends LEvents with Logging {
  implicit val formats = DefaultFormats.lossless
  private val estype = "events"
  private val seq = new ESSequences(client, config, index)

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

  override
  def init(appId: Int, channelId: Option[Int]): Boolean = {
    // check namespace exist
    // TODO: 初期化はshellなどでできるようにする？
    implicit val formats = DefaultFormats

    val indices = client.admin.indices
    val indexExistResponse = indices.prepareExists(index).get
    if (!indexExistResponse.isExists) {
      indices.prepareCreate(index).get
    }
    val typeExistResponse = indices.prepareTypesExists(index).setTypes(estype).get
    if (!typeExistResponse.isExists) {
      // TODO: マッピングはテンプレートで扱うデータに応じて動的に生成すべきなのでは。。。
      val json =
        (estype ->
          ("properties" ->
            ("eventId" -> ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
              ("event" -> ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
              ("entityType" -> ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
              ("entityId" -> ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
              ("targetEntityType" -> ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
              ("targetEntityId" -> ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
              ("properties" ->
                ("type" -> "nested") ~
                  ("properties" ->
                    ("fields" -> ("type" -> "nested") ~
                      ("properties" ->
                        ("user" -> ("type" -> "long")) ~
                          ("num" -> ("type" -> "long"))
                        )))) ~
              ("eventTime" -> ("type" -> "date")) ~
              ("tags" -> ("type" -> "array")) ~
              ("prId" -> ("type" -> "string") ~ ("index" -> "not_analyzed")) ~
              ("creationTime" -> ("type" -> "date"))))
      indices.preparePutMapping(index).setType(estype).
        setSource(compact(render(json))).get
    }

    true
  }

  override
  def remove(appId: Int, channelId: Option[Int] = None): Boolean = {
    true
  }

  override
  def close(): Unit = {
    // client.admin.close()
    // client.connection.close()
  }

  override
  def futureInsert(
    event: Event, appId: Int, channelId: Option[Int])(implicit ec: ExecutionContext):
    Future[String] = {
    // TODO: Future[Either]で返すようにする
    Future {
      val id = event.eventId.getOrElse {
        var roll = seq.genNext("events")
        while (!get(roll).isEmpty) roll = seq.genNext("events")
        roll.toString
      }
      try {
        val response = client.prepareIndex(index, estype, id).
          setSource(write(event)).get()
      } catch {
        case e: ElasticsearchException =>
          error(e.getMessage)
      }
      id
    }
  }

  def get(id: Int): Option[Event] = { // TODO: private にしたほうがいいのでは（ESAppsにならった）
    try {
      val response = client.prepareGet(
        index,
        estype,
        id.toString).get()
      Some(read[Event](response.getSourceAsString))
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        None
      case e: NullPointerException => None
    }
  }

  override
  def futureGet(
    eventId: String, appId: Int, channelId: Option[Int])(implicit ec: ExecutionContext):
    Future[Option[Event]] = {
    // TODO: Future[Either]で返すようにする
      Future {
        try {
          val response = client.prepareGet(
            index,
            estype,
            eventId).get()
          Some(read[Event](response.getSourceAsString))
        } catch {
          case e: ElasticsearchException =>
            error(e.getMessage)
            None
          case e: NullPointerException => None
        }
      }
    }

  override
  def futureDelete(
    eventId: String, appId: Int)(implicit ec: ExecutionContext):
  Future[Boolean] = {
    // TODO: 実装する
    Future {
      true
    }
  }

  override
  def futureDelete(
    eventId: String, appId: Int, channelId: Option[Int])(implicit ec: ExecutionContext):
    Future[Boolean] = {
    // TODO: 実装する
    Future {
      true
    }
  }

  override
  def futureFind(
    appId: Int,
    channelId: Option[Int] = None,
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None,
    entityType: Option[String] = None,
    entityId: Option[String] = None,
    eventNames: Option[Seq[String]] = None,
    targetEntityType: Option[Option[String]] = None,
    targetEntityId: Option[Option[String]] = None,
    limit: Option[Int] = None,
    reversed: Option[Boolean] = None
  )(implicit ec: ExecutionContext): Future[Iterator[Event]] = {
    // TODO: FutureMapとか使うべきなのでは。。。
    Future {
      try {
        val builder = client.prepareSearch(index).setTypes(estype)
        ESUtils.getAll[Event](client, builder).toIterator
      } catch {
        case e: ElasticsearchException =>
          error(e.getMessage)
          Iterator[Event]()
      }
    }
  }

}
