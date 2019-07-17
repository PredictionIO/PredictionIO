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

import scala.collection.JavaConversions._

import org.apache.http.entity.ContentType
import org.apache.http.nio.entity.NStringEntity
import org.elasticsearch.client.RestClient
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.read
import org.apache.http.util.EntityUtils
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.DateTimeZone
import org.apache.predictionio.data.storage.StorageClientConfig
import org.apache.http.HttpHost
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.DataMap

object ESUtils {
  val scrollLife = "1m"

  def toEvent(value: JValue)(
    implicit formats: Formats): Event = {
    def getString(s: String): String = {
      (value \ s) match {
        case JNothing => null
        case x => x.extract[String]
      }
    }

    def getOptString(s: String): Option[String] = {
      Option(getString(s))
    }

    val properties: DataMap = getOptString("properties")
      .map(s => DataMap(read[JObject](s))).getOrElse(DataMap())
    val eventId = getOptString("eventId")
    val event = getString("event")
    val entityType = getString("entityType")
    val entityId = getString("entityId")
    val targetEntityType = getOptString("targetEntityType")
    val targetEntityId = getOptString("targetEntityId")
    val prId = getOptString("prId")
    val eventTime: DateTime = ESUtils.parseUTCDateTime(getString("eventTime"))
    val creationTime: DateTime = ESUtils.parseUTCDateTime(getString("creationTime"))
    val tags = (value \ "tags").extract[Seq[String]]

    Event(
      eventId = eventId,
      event = event,
      entityType = entityType,
      entityId = entityId,
      targetEntityType = targetEntityType,
      targetEntityId = targetEntityId,
      properties = properties,
      eventTime = eventTime,
      tags = tags,
      prId = prId,
      creationTime = creationTime)
  }

  def getEvents(
    client: RestClient,
    index: String,
    query: String,
    size: Int)(
      implicit formats: Formats): Seq[Event] = {
    getDocList(client, index, query, size).map(x => toEvent(x))
  }

  def getDocList(
    client: RestClient,
    index: String,
    query: String,
    size: Int)(
      implicit formats: Formats): Seq[JValue] = {
    val entity = new NStringEntity(query, ContentType.APPLICATION_JSON)
    val response = client.performRequest(
      "POST",
      s"/$index/_search",
      Map("size" -> s"${size}"),
      entity)
    val responseJValue = parse(EntityUtils.toString(response.getEntity))
    val hits = (responseJValue \ "hits" \ "hits").extract[Seq[JValue]]
    hits.map(h => (h \ "_source"))
  }

  def getAll[T: Manifest](
    client: RestClient,
    index: String,
    query: String)(
      implicit formats: Formats): Seq[T] = {
    getDocAll(client, index, query).map(x => x.extract[T])
  }

  def getEventAll(
    client: RestClient,
    index: String,
    query: String)(
      implicit formats: Formats): Seq[Event] = {
    getDocAll(client, index, query).map(x => toEvent(x))
  }

  def getDocAll(
    client: RestClient,
    index: String,
    query: String)(
      implicit formats: Formats): Seq[JValue] = {

    @scala.annotation.tailrec
    def scroll(scrollId: String, hits: Seq[JValue], results: Seq[JValue]): Seq[JValue] = {
      if (hits.isEmpty) results
      else {
        val json = ("scroll" -> scrollLife) ~ ("scroll_id" -> scrollId)
        val scrollBody = new NStringEntity(compact(render(json)), ContentType.APPLICATION_JSON)
        val response = client.performRequest(
          "POST",
          "/_search/scroll",
          Map[String, String](),
          scrollBody)
        val responseJValue = parse(EntityUtils.toString(response.getEntity))
        scroll((responseJValue \ "_scroll_id").extract[String],
          (responseJValue \ "hits" \ "hits").extract[Seq[JValue]],
          results ++ hits.map(h => (h \ "_source").extract[JValue]))
      }
    }

    val entity = new NStringEntity(query, ContentType.APPLICATION_JSON)
    val response = client.performRequest(
      "POST",
      s"/$index/_search",
      Map("scroll" -> scrollLife),
      entity)
    val responseJValue = parse(EntityUtils.toString(response.getEntity))
    scroll((responseJValue \ "_scroll_id").extract[String],
      (responseJValue \ "hits" \ "hits").extract[Seq[JValue]],
      Nil)
  }

  def createIndex(
    client: RestClient,
    index: String,
    json: String)(
      implicit formats: Formats): String = {
    client.performRequest(
      "HEAD",
      s"/$index",
      Map("include_type_name" -> "false")).getStatusLine.getStatusCode match {
        case 404 =>
          val entity = new NStringEntity(json, ContentType.APPLICATION_JSON)
          client.performRequest(
            "PUT",
            s"/$index",
            Map("include_type_name" -> "false"),
            entity).getStatusLine.getStatusCode match {
              case 200 =>
                "_doc"
              case _ =>
                throw new IllegalStateException(s"/$index is invalid: $json")
            }
        case 200 =>
          esType(client, index)
        case _ =>
          throw new IllegalStateException(s"/$index is invalid: $json")
      }
  }

  // We cannot have several types within a single index as of ES 6.0, so
  // continue to add or update a document under the current type. This code is
  // a step towards ES 7.0 support (removal of mapping types).
  def esType(
    client: RestClient,
    index: String)(
      implicit formats: Formats): String = {
    val response = client.performRequest(
      "GET",
      s"/$index",
      Map("include_type_name" -> "true"))
    response.getStatusLine.getStatusCode match {
      case 200 =>
        (parse(EntityUtils.toString(response.getEntity)) \ index \ "mappings")
          .extract[JObject].values.collectFirst {
          case (name, _) if name != "_doc" && name != "properties" => name
        }.getOrElse("_doc")
      case _ =>
        throw new IllegalStateException(s"/$index is invalid.")
    }
  }

  def formatUTCDateTime(dt: DateTime): String = {
    DateTimeFormat
      .forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").print(dt.withZone(DateTimeZone.UTC))
  }

  def parseUTCDateTime(str: String): DateTime = {
    DateTimeFormat
      .forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parseDateTime(str)
  }

  def createEventQuery(
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None,
    entityType: Option[String] = None,
    entityId: Option[String] = None,
    eventNames: Option[Seq[String]] = None,
    targetEntityType: Option[Option[String]] = None,
    targetEntityId: Option[Option[String]] = None,
    reversed: Option[Boolean] = None): String = {
    val mustQueries = Seq(
      startTime.map { x =>
        val v = formatUTCDateTime(x)
        s"""{"range":{"eventTime":{"gte":"${v}"}}}"""
      },
      untilTime.map { x =>
        val v = formatUTCDateTime(x)
        s"""{"range":{"eventTime":{"lt":"${v}"}}}"""
      },
      entityType.map(x => s"""{"term":{"entityType":"${x}"}}"""),
      entityId.map(x => s"""{"term":{"entityId":"${x}"}}"""),
      targetEntityType.flatMap(xx => xx.map(x => s"""{"term":{"targetEntityType":"${x}"}}""")),
      targetEntityId.flatMap(xx => xx.map(x => s"""{"term":{"targetEntityId":"${x}"}}""")),
      eventNames
        .map { xx => xx.map(x => "\"%s\"".format(x)) }
        .map(x => s"""{"terms":{"event":[${x.mkString(",")}]}}""")).flatten.mkString(",")
    val query = mustQueries.isEmpty match {
      case true => """query":{"match_all":{}}"""
      case _ => s"""query":{"bool":{"must":[${mustQueries}]}}"""
    }
    val sortOrder = reversed.map(x => x match {
      case true => "desc"
      case _ => "asc"
    }).getOrElse("asc")
    s"""{
       |"${query},
       |"sort":[{"eventTime":{"order":"${sortOrder}"}}]
       |}""".stripMargin
  }

  def getHttpHosts(config: StorageClientConfig): Seq[HttpHost] = {
    val hosts = config.properties.get("HOSTS").
      map(_.split(",").toSeq).getOrElse(Seq("localhost"))
    val ports = config.properties.get("PORTS").
      map(_.split(",").toSeq.map(_.toInt)).getOrElse(Seq(9200))
    val schemes = config.properties.get("SCHEMES").
      map(_.split(",").toSeq).getOrElse(Seq("http"))
    (hosts, ports, schemes).zipped.map((h, p, s) => new HttpHost(h, p, s))
  }

  def getEventDataRefresh(config: StorageClientConfig): String = {
    config.properties.getOrElse("EVENTDATA_REFRESH", "true")
  }
}
