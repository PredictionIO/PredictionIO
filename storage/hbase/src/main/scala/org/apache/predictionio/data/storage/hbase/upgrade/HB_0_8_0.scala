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


package org.apache.predictionio.data.storage.hbase.upgrade

import org.apache.predictionio.annotation.Experimental

import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.EventValidation
import org.apache.predictionio.data.storage.DataMap

import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.client.HConnection
import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.util.Bytes

import org.joda.time.DateTime
import org.joda.time.DateTimeZone

import org.json4s.DefaultFormats
import org.json4s.JObject
import org.json4s.native.Serialization.{ read, write }

import org.apache.commons.codec.binary.Base64

import scala.collection.JavaConversions._

/** :: Experimental :: */
@Experimental
object HB_0_8_0 {

  implicit val formats = DefaultFormats

  def getByAppId(
    connection: HConnection,
    namespace: String,
    appId: Int): Iterator[Event] = {
    val tableName = TableName.valueOf(namespace, "events")
    val table = connection.getTable(tableName)
    val start = PartialRowKey(appId)
    val stop = PartialRowKey(appId + 1)
    val scan = new Scan(start.toBytes, stop.toBytes)
    val scanner = table.getScanner(scan)
    table.close()
    scanner.iterator().map { resultToEvent(_) }
  }

  val colNames: Map[String, Array[Byte]] = Map(
    "event" -> "e",
    "entityType" -> "ety",
    "entityId" -> "eid",
    "targetEntityType" -> "tety",
    "targetEntityId" -> "teid",
    "properties" -> "p",
    "prId" -> "pk", // columna name is 'pk' in 0.8.0/0.8.1
    "eventTimeZone" -> "etz",
    "creationTimeZone" -> "ctz"
  ).mapValues(Bytes.toBytes(_))


  class RowKey(
    val appId: Int,
    val millis: Long,
    val uuidLow: Long
  ) {
    lazy val toBytes: Array[Byte] = {
      // add UUID least significant bits for multiple actions at the same time
      // (UUID's most significant bits are actually timestamp,
      // use eventTime instead).
      Bytes.toBytes(appId) ++ Bytes.toBytes(millis) ++ Bytes.toBytes(uuidLow)
    }
    override def toString: String = {
      Base64.encodeBase64URLSafeString(toBytes)
    }
  }

  object RowKey {
    // get RowKey from string representation
    def apply(s: String): RowKey = {
      try {
        apply(Base64.decodeBase64(s))
      } catch {
        case e: Exception => throw new RowKeyException(
          s"Failed to convert String ${s} to RowKey because ${e}", e)
      }
    }

    def apply(b: Array[Byte]): RowKey = {
      if (b.size != 20) {
        val bString = b.mkString(",")
        throw new RowKeyException(
          s"Incorrect byte array size. Bytes: ${bString}.")
      }

      new RowKey(
        appId = Bytes.toInt(b.slice(0, 4)),
        millis = Bytes.toLong(b.slice(4, 12)),
        uuidLow = Bytes.toLong(b.slice(12, 20))
      )
    }
  }

  class RowKeyException(msg: String, cause: Exception)
    extends Exception(msg, cause) {
      def this(msg: String) = this(msg, null)
    }

  case class PartialRowKey(val appId: Int, val millis: Option[Long] = None) {
    val toBytes: Array[Byte] = {
      Bytes.toBytes(appId) ++
        (millis.map(Bytes.toBytes(_)).getOrElse(Array[Byte]()))
    }
  }

  def resultToEvent(result: Result): Event = {
    val rowKey = RowKey(result.getRow())

    val eBytes = Bytes.toBytes("e")
    // val e = result.getFamilyMap(eBytes)

    def getStringCol(col: String): String = {
      val r = result.getValue(eBytes, colNames(col))
      require(r != null,
        s"Failed to get value for column ${col}. " +
        s"Rowkey: ${rowKey.toString} " +
        s"StringBinary: ${Bytes.toStringBinary(result.getRow())}.")

      Bytes.toString(r)
    }

    def getOptStringCol(col: String): Option[String] = {
      val r = result.getValue(eBytes, colNames(col))
      if (r == null) {
        None
      } else {
        Some(Bytes.toString(r))
      }
    }

    def getTimestamp(col: String): Long = {
      result.getColumnLatestCell(eBytes, colNames(col)).getTimestamp()
    }

    val event = getStringCol("event")
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
    val creationTimeZone = getOptStringCol("creationTimeZone")
      .map(DateTimeZone.forID(_))
      .getOrElse(EventValidation.defaultTimeZone)

    val creationTime: DateTime = new DateTime(
      getTimestamp("event"), creationTimeZone
    )

    Event(
      eventId = Some(RowKey(result.getRow()).toString),
      event = event,
      entityType = entityType,
      entityId = entityId,
      targetEntityType = targetEntityType,
      targetEntityId = targetEntityId,
      properties = properties,
      eventTime = new DateTime(rowKey.millis, eventTimeZone),
      tags = Seq(),
      prId = prId,
      creationTime = creationTime
    )
  }
}
