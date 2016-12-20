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

import org.apache.hadoop.io.{LongWritable, Text, MapWritable}
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.EventValidation
import org.apache.predictionio.data.storage.DataMap

import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.filter.FilterList
import org.apache.hadoop.hbase.filter.RegexStringComparator
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp
import org.apache.hadoop.hbase.filter.BinaryComparator
import org.apache.hadoop.hbase.filter.QualifierFilter
import org.apache.hadoop.hbase.filter.SkipFilter

import org.json4s.DefaultFormats
import org.json4s.JObject
import org.json4s.native.Serialization.{ read, write }

import org.joda.time.DateTime
import org.joda.time.DateTimeZone

import org.apache.commons.codec.binary.Base64
import java.security.MessageDigest

import java.util.UUID

object ESEventsUtil {

  implicit val formats = DefaultFormats

  def resultToEvent(result: MapWritable, appId: Int): Event = {

    def getStringCol(col: String): String = {
      val r = result.get(col).asInstanceOf[Text]
      require(r != null,
        s"Failed to get value for column ${col}. " +
          s"StringBinary: ${r.getBytes()}.")

      r.toString()
    }

    def getLongCol(col: String): Long = {
      val r = result.get(col).asInstanceOf[LongWritable]
      require(r != null,
        s"Failed to get value for column ${col}. " +
          s"StringBinary: ${r.get()}.")

      r.get()
    }

    def getOptStringCol(col: String): Option[String] = {
      val r = result.get(col).asInstanceOf[Text]
      if (r == null) {
        None
      } else {
        Some(r.toString())
      }
    }

    // TODO: elasticsearch timestamp format
    def getTimestamp(col: String): Long = {
      result.get(col).asInstanceOf[LongWritable].get()
    }

    val eventId = None // TODO: use `_id` field?
//    val event = getStringCol("event")
    val entityType = getStringCol("entityType")
    val entityId = getStringCol("entityId")
    val targetEntityType = getOptStringCol("targetEntityType")
    val targetEntityId = getOptStringCol("targetEntityId")
    val properties: DataMap = getOptStringCol("properties")
      .map(s => DataMap(read[JObject](s))).getOrElse(DataMap())
    val prId = getOptStringCol("prId")
    val eventTimeZone = getOptStringCol("eventTimeZone")
      .map(DateTimeZone.forID(_))
      .getOrElse(EventValidation.defaultTimeZone)
    val eventTime = new DateTime(
      getLongCol("eventTime"), eventTimeZone)
    val creationTimeZone = getOptStringCol("creationTimeZone")
      .map(DateTimeZone.forID(_))
      .getOrElse(EventValidation.defaultTimeZone)
    val creationTime: DateTime = new DateTime(
      getLongCol("creationTime"), creationTimeZone)

    Event(
      eventId = eventId,
      event = "event",
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

  def eventToPut(event: Event, appId: Int): Seq[Map[String, Any]] = {
    Seq(
      Map(
        "eventId" -> event.eventId,
        "event" -> event.event,
        "entityType" -> event.entityType,
        "entityId" -> event.entityId,
        "targetEntityType" -> event.targetEntityType,
        "targetEntityId" -> event.targetEntityId,
        "properties" -> event.properties,
        "eventTime" -> event.eventTime,
        "tags" -> event.tags,
        "prId" -> event.prId,
        "creationTime" -> event.creationTime
      )
    )
  }

}
