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

import org.apache.hadoop.io.MapWritable
import org.apache.hadoop.io.Text
import org.apache.predictionio.data.storage.DataMap
import org.apache.predictionio.data.storage.Event
import org.joda.time.DateTime
import org.json4s._
import org.json4s.native.Serialization.read
import org.json4s.native.Serialization.write

object ESEventsUtil {

  implicit val formats = DefaultFormats

  def resultToEvent(id: Text, result: MapWritable, appId: Int): Event = {

    def getStringCol(col: String): String = {
      val r = result.get(new Text(col)).asInstanceOf[Text]
      require(r != null,
        s"Failed to get value for column ${col}. " +
          s"StringBinary: ${r.getBytes()}.")

      r.toString()
    }

    def getOptStringCol(col: String): Option[String] = {
      val r = result.get(new Text(col))
      if (r == null) {
        None
      } else {
        Some(r.asInstanceOf[Text].toString())
      }
    }

    val properties: DataMap = getOptStringCol("properties")
      .map(s => DataMap(read[JObject](s))).getOrElse(DataMap())
    val eventId = Some(getStringCol("eventId"))
    val event = getStringCol("event")
    val entityType = getStringCol("entityType")
    val entityId = getStringCol("entityId")
    val targetEntityType = getOptStringCol("targetEntityType")
    val targetEntityId = getOptStringCol("targetEntityId")
    val prId = getOptStringCol("prId")
    val eventTime: DateTime = ESUtils.parseUTCDateTime(getStringCol("eventTime"))
    val creationTime: DateTime = ESUtils.parseUTCDateTime(getStringCol("creationTime"))

    Event(
      eventId = eventId,
      event = event,
      entityType = entityType,
      entityId = entityId,
      targetEntityType = targetEntityType,
      targetEntityId = targetEntityId,
      properties = properties,
      eventTime = eventTime,
      tags = Seq(),
      prId = prId,
      creationTime = creationTime
    )
  }

  def eventToPut(event: Event, appId: Int): Map[String, Any] = {
    Map(
      "eventId" -> event.eventId,
      "event" -> event.event,
      "entityType" -> event.entityType,
      "entityId" -> event.entityId,
      "targetEntityType" -> event.targetEntityType,
      "targetEntityId" -> event.targetEntityId,
      "properties" -> write(event.properties.toJObject),
      "eventTime" -> ESUtils.formatUTCDateTime(event.eventTime),
      "tags" -> event.tags,
      "prId" -> event.prId,
      "creationTime" -> ESUtils.formatUTCDateTime(event.creationTime)
    )
  }

}
