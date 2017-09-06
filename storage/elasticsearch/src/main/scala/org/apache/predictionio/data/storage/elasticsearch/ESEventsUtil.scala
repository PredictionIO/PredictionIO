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

import java.net.NetworkInterface
import java.net.SocketException
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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
      result.get(new Text(col)) match {
        case x if x.isInstanceOf[Text] => Some(x.asInstanceOf[Text].toString)
        case _ => None
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
      tags = Nil,
      prId = prId,
      creationTime = creationTime
    )
  }

  def eventToPut(event: Event, appId: Int): Map[String, Any] = {
    Map(
      "eventId" -> event.eventId.getOrElse { getBase64UUID },
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

  val secureRandom: SecureRandom = new SecureRandom()

  val sequenceNumber: AtomicInteger = new AtomicInteger(secureRandom.nextInt())

  val lastTimestamp: AtomicLong = new AtomicLong(0)

  val secureMungedAddress: Array[Byte] = {
    val address = getMacAddress match {
      case Some(x) => x
      case None =>
        val dummy: Array[Byte] = new Array[Byte](6)
        secureRandom.nextBytes(dummy)
        dummy(0) = (dummy(0) | 0x01.toByte).toByte
        dummy
    }

    val mungedBytes: Array[Byte] = new Array[Byte](6)
    secureRandom.nextBytes(mungedBytes)
    for (i <- 0 until 6) {
      mungedBytes(i) = (mungedBytes(i) ^ address(i)).toByte
    }

    mungedBytes
  }

  def getMacAddress(): Option[Array[Byte]] = {
    try {
      NetworkInterface.getNetworkInterfaces match {
        case en if en == null => None
        case en =>
          new Iterator[NetworkInterface] {
            def next = en.nextElement
            def hasNext = en.hasMoreElements
          }.foldLeft(None: Option[Array[Byte]])((x, y) =>
            x match {
              case None =>
                y.isLoopback match {
                  case true =>
                    y.getHardwareAddress match {
                      case address if isValidAddress(address) => Some(address)
                      case _ => None
                    }
                  case false => None
                }
              case _ => x
            })
      }
    } catch {
      case e: SocketException => None
    }
  }

  def isValidAddress(address: Array[Byte]): Boolean = {
    address match {
      case v if v == null || v.length != 6 => false
      case v => v.exists(b => b != 0x00.toByte)
    }
  }

  def putLong(array: Array[Byte], l: Long, pos: Int, numberOfLongBytes: Int): Unit = {
    for (i <- 0 until numberOfLongBytes) {
      array(pos + numberOfLongBytes - i - 1) = (l >>> (i * 8)).toByte
    }
  }

  def getBase64UUID(): String = {
    val sequenceId: Int = sequenceNumber.incrementAndGet & 0xffffff
    val timestamp: Long = synchronized {
      val t = Math.max(lastTimestamp.get, System.currentTimeMillis)
      if (sequenceId == 0) {
        lastTimestamp.set(t + 1)
      } else {
        lastTimestamp.set(t)
      }
      lastTimestamp.get
    }

    val uuidBytes: Array[Byte] = new Array[Byte](15)

    putLong(uuidBytes, timestamp, 0, 6)
    System.arraycopy(secureMungedAddress, 0, uuidBytes, 6, secureMungedAddress.length)
    putLong(uuidBytes, sequenceId, 12, 3)

    Base64.getUrlEncoder().withoutPadding().encodeToString(uuidBytes)
  }
}
