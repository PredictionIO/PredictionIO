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
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.nio.entity.NStringEntity
import org.apache.http.util.EntityUtils
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.LEvents
import org.apache.predictionio.data.storage.StorageClientConfig
import org.elasticsearch.client.RestClient
import org.joda.time.DateTime
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write
import org.json4s.ext.JodaTimeSerializers
import grizzled.slf4j.Logging
import org.apache.http.message.BasicHeader

class ESLEvents(val client: RestClient, config: StorageClientConfig, val eventdataName: String)
    extends LEvents with Logging {
  implicit val formats = DefaultFormats.lossless ++ JodaTimeSerializers.all

  def eventdataKey(appId: Int, channelId: Option[Int] = None): String = {
    channelId.map { ch =>
      s"${appId}_${ch}"
    }.getOrElse {
      s"${appId}"
    }
  }

  override def init(appId: Int, channelId: Option[Int] = None): Boolean = {
    val index = eventdataName + "_" + eventdataKey(appId, channelId)
    val json =
      ("mappings" ->
        ("properties" ->
          ("name" -> ("type" -> "keyword")) ~
          ("eventId" -> ("type" -> "keyword")) ~
          ("event" -> ("type" -> "keyword")) ~
          ("entityType" -> ("type" -> "keyword")) ~
          ("entityId" -> ("type" -> "keyword")) ~
          ("targetEntityType" -> ("type" -> "keyword")) ~
          ("targetEntityId" -> ("type" -> "keyword")) ~
          ("properties" -> ("enabled" -> false)) ~
          ("eventTime" -> ("type" -> "date")) ~
          ("tags" -> ("type" -> "keyword")) ~
          ("prId" -> ("type" -> "keyword")) ~
          ("creationTime" -> ("type" -> "date"))))
    ESUtils.createIndex(client, index, compact(render(json)))
    true
  }

  override def remove(appId: Int, channelId: Option[Int] = None): Boolean = {
    val index = eventdataName + "_" + eventdataKey(appId, channelId)
    try {
      client.performRequest(
        "DELETE",
        s"/$index",
        Map.empty[String, String].asJava
      ).getStatusLine.getStatusCode match {
        case 200 => true
        case _ =>
          error(s"Failed to remove $index")
          false
      }
    } catch {
      case e: Exception =>
        error(s"Failed to remove $index", e)
        false
    }
  }

  override def close(): Unit = {}

  override def futureInsert(
    event: Event,
    appId: Int,
    channelId: Option[Int])(implicit ec: ExecutionContext): Future[String] = {
    Future {
      val index = eventdataName + "_" + eventdataKey(appId, channelId)
      val estype = ESUtils.esType(client, index)
      try {
        val id = event.eventId.getOrElse {
          ESEventsUtil.getBase64UUID
        }
        val json =
          ("eventId" -> id) ~
          ("event" -> event.event) ~
          ("entityType" -> event.entityType) ~
          ("entityId" -> event.entityId) ~
          ("targetEntityType" -> event.targetEntityType) ~
          ("targetEntityId" -> event.targetEntityId) ~
          ("eventTime" -> ESUtils.formatUTCDateTime(event.eventTime)) ~
          ("tags" -> event.tags) ~
          ("prId" -> event.prId) ~
          ("creationTime" -> ESUtils.formatUTCDateTime(event.creationTime)) ~
          ("properties" -> write(event.properties.toJObject))
        val entity = new NStringEntity(compact(render(json)), ContentType.APPLICATION_JSON)
        val response = client.performRequest(
          "PUT",
          s"/$index/$estype/$id",
          Map("refresh" -> ESUtils.getEventDataRefresh(config)).asJava,
          entity)
        val jsonResponse = parse(EntityUtils.toString(response.getEntity))
        val result = (jsonResponse \ "result").extract[String]
        result match {
          case "created" => id
          case _ =>
            error(s"[$result] Failed to update $index/$estype/$id")
            ""
        }
      } catch {
        case e: IOException =>
          error(s"Failed to update $index/$estype/<id>", e)
          ""
      }
    }
  }

  override def futureInsertBatch(
    events: Seq[Event],
    appId: Int,
    channelId: Option[Int])(implicit ec: ExecutionContext): Future[Seq[String]] = {
    Future {
      val index = eventdataName + "_" + eventdataKey(appId, channelId)
      val estype = ESUtils.esType(client, index)
      try {
        val ids = events.map { event =>
          event.eventId.getOrElse(ESEventsUtil.getBase64UUID)
        }

        val json = events.zip(ids).map { case (event, id) =>
          val commandJson =
            ("create" -> (
              ("_index" -> index) ~
              ("_type" -> estype) ~
              ("_id" -> id)
            ))

          val documentJson =
            ("eventId" -> id) ~
            ("event" -> event.event) ~
            ("entityType" -> event.entityType) ~
            ("entityId" -> event.entityId) ~
            ("targetEntityType" -> event.targetEntityType) ~
            ("targetEntityId" -> event.targetEntityId) ~
            ("eventTime" -> ESUtils.formatUTCDateTime(event.eventTime)) ~
            ("tags" -> event.tags) ~
            ("prId" -> event.prId) ~
            ("creationTime" -> ESUtils.formatUTCDateTime(event.creationTime)) ~
            ("properties" -> write(event.properties.toJObject))

          compact(render(commandJson)) + "\n" + compact(render(documentJson))

        }.mkString("", "\n", "\n")

        val entity = new StringEntity(json)
        val response = client.performRequest(
          "POST",
          "/_bulk",
          Map("refresh" -> ESUtils.getEventDataRefresh(config)).asJava,
          entity,
          new BasicHeader("Content-Type", "application/x-ndjson"))

        val responseJson = parse(EntityUtils.toString(response.getEntity))
        val items = (responseJson \ "items").asInstanceOf[JArray]

        items.arr.map { case value: JObject =>
          val result = (value \ "create" \ "result").extract[String]
          val id = (value \ "create" \ "_id").extract[String]

          result match {
            case "created" => id
            case _ =>
              error(s"[$result] Failed to update $index/$estype/$id")
              ""
          }
        }
      } catch {
        case e: IOException =>
          error(s"Failed to update $index/$estype/<id>", e)
          Nil
      }
    }
  }

  override def futureGet(
    eventId: String,
    appId: Int,
    channelId: Option[Int])(implicit ec: ExecutionContext): Future[Option[Event]] = {
    Future {
      val index = eventdataName + "_" + eventdataKey(appId, channelId)
      try {
        val json =
          ("query" ->
            ("term" ->
              ("eventId" -> eventId)))
        val entity = new NStringEntity(compact(render(json)), ContentType.APPLICATION_JSON)
        val response = client.performRequest(
          "POST",
          s"/$index/_search",
          Map.empty[String, String].asJava,
          entity)
        val jsonResponse = parse(EntityUtils.toString(response.getEntity))
        val results = (jsonResponse \ "hits" \ "hits").extract[Seq[JValue]]
        results.headOption.map { jv =>
          (jv \ "_source").extract[Event]
        }
      } catch {
        case e: IOException =>
          error(s"Failed to access to /$index/_search", e)
          None
      }
    }
  }

  override def futureDelete(
    eventId: String,
    appId: Int,
    channelId: Option[Int])(implicit ec: ExecutionContext): Future[Boolean] = {
    Future {
      val index = eventdataName + "_" + eventdataKey(appId, channelId)
      try {
        val json =
          ("query" ->
            ("term" ->
              ("eventId" -> eventId)))
        val entity = new NStringEntity(compact(render(json)), ContentType.APPLICATION_JSON)
        val response = client.performRequest(
          "POST",
          s"/$index/_delete_by_query",
          Map("refresh" -> ESUtils.getEventDataRefresh(config)).asJava,
          entity)
        val jsonResponse = parse(EntityUtils.toString(response.getEntity))
        (jsonResponse \ "deleted").extract[Int] > 0
      } catch {
        case e: IOException =>
          error(s"Failed to delete $index:$eventId", e)
          false
      }
    }
  }

  override def futureFind(
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
    reversed: Option[Boolean] = None)
    (implicit ec: ExecutionContext): Future[Iterator[Event]] = {
    Future {
      val index = eventdataName + "_" + eventdataKey(appId, channelId)
      try {
        val query = ESUtils.createEventQuery(
          startTime, untilTime, entityType, entityId,
          eventNames, targetEntityType, targetEntityId, reversed)
        limit.getOrElse(20) match {
          case -1 => ESUtils.getEventAll(client, index, query).toIterator
          case size => ESUtils.getEvents(client, index, query, size).toIterator
        }
      } catch {
        case e: IOException =>
          error(e.getMessage)
          Iterator.empty
      }
    }
  }

}
