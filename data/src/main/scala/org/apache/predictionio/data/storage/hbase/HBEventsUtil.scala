/** Copyright 2015 TappingStone, Inc.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package org.apache.predictionio.data.storage.hbase

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

/* common utility function for accessing EventsStore in HBase */
object HBEventsUtil {

  implicit val formats = DefaultFormats

  def tableName(namespace: String, appId: Int, channelId: Option[Int] = None): String = {
    channelId.map { ch =>
      s"${namespace}:events_${appId}_${ch}"
    }.getOrElse {
      s"${namespace}:events_${appId}"
    }
  }

  // column names for "e" column family
  val colNames: Map[String, Array[Byte]] = Map(
    "event" -> "e",
    "entityType" -> "ety",
    "entityId" -> "eid",
    "targetEntityType" -> "tety",
    "targetEntityId" -> "teid",
    "properties" -> "p",
    "prId" -> "prid",
    "eventTime" -> "et",
    "eventTimeZone" -> "etz",
    "creationTime" -> "ct",
    "creationTimeZone" -> "ctz"
  ).mapValues(Bytes.toBytes(_))

  def hash(entityType: String, entityId: String): Array[Byte] = {
    val s = entityType + "-" + entityId
    // get a new MessageDigest object each time for thread-safe
    val md5 = MessageDigest.getInstance("MD5")
    md5.digest(Bytes.toBytes(s))
  }

  class RowKey(
    val b: Array[Byte]
  ) {
    require((b.size == 32), s"Incorrect b size: ${b.size}")
    lazy val entityHash: Array[Byte] = b.slice(0, 16)
    lazy val millis: Long = Bytes.toLong(b.slice(16, 24))
    lazy val uuidLow: Long = Bytes.toLong(b.slice(24, 32))

    lazy val toBytes: Array[Byte] = b

    override def toString: String = {
      Base64.encodeBase64URLSafeString(toBytes)
    }
  }

  object RowKey {
    def apply(
      entityType: String,
      entityId: String,
      millis: Long,
      uuidLow: Long): RowKey = {
        // add UUID least significant bits for multiple actions at the same time
        // (UUID's most significant bits are actually timestamp,
        // use eventTime instead).
        val b = hash(entityType, entityId) ++
          Bytes.toBytes(millis) ++ Bytes.toBytes(uuidLow)
        new RowKey(b)
      }

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
      if (b.size != 32) {
        val bString = b.mkString(",")
        throw new RowKeyException(
          s"Incorrect byte array size. Bytes: ${bString}.")
      }
      new RowKey(b)
    }

  }

  class RowKeyException(val msg: String, val cause: Exception)
    extends Exception(msg, cause) {
      def this(msg: String) = this(msg, null)
    }

  case class PartialRowKey(entityType: String, entityId: String,
    millis: Option[Long] = None) {
    val toBytes: Array[Byte] = {
      hash(entityType, entityId) ++
        (millis.map(Bytes.toBytes(_)).getOrElse(Array[Byte]()))
    }
  }

  def eventToPut(event: Event, appId: Int): (Put, RowKey) = {
    // generate new rowKey if eventId is None
    val rowKey = event.eventId.map { id =>
      RowKey(id) // create rowKey from eventId
    }.getOrElse {
      // TOOD: use real UUID. not pseudo random
      val uuidLow: Long = UUID.randomUUID().getLeastSignificantBits
      RowKey(
        entityType = event.entityType,
        entityId = event.entityId,
        millis = event.eventTime.getMillis,
        uuidLow = uuidLow
      )
    }

    val eBytes = Bytes.toBytes("e")
    // use eventTime as HBase's cell timestamp
    val put = new Put(rowKey.toBytes, event.eventTime.getMillis)

    def addStringToE(col: Array[Byte], v: String): Put = {
      put.add(eBytes, col, Bytes.toBytes(v))
    }

    def addLongToE(col: Array[Byte], v: Long): Put = {
      put.add(eBytes, col, Bytes.toBytes(v))
    }

    addStringToE(colNames("event"), event.event)
    addStringToE(colNames("entityType"), event.entityType)
    addStringToE(colNames("entityId"), event.entityId)

    event.targetEntityType.foreach { targetEntityType =>
      addStringToE(colNames("targetEntityType"), targetEntityType)
    }

    event.targetEntityId.foreach { targetEntityId =>
      addStringToE(colNames("targetEntityId"), targetEntityId)
    }

    // TODO: make properties Option[]
    if (!event.properties.isEmpty) {
      addStringToE(colNames("properties"), write(event.properties.toJObject))
    }

    event.prId.foreach { prId =>
      addStringToE(colNames("prId"), prId)
    }

    addLongToE(colNames("eventTime"), event.eventTime.getMillis)
    val eventTimeZone = event.eventTime.getZone
    if (!eventTimeZone.equals(EventValidation.defaultTimeZone)) {
      addStringToE(colNames("eventTimeZone"), eventTimeZone.getID)
    }

    addLongToE(colNames("creationTime"), event.creationTime.getMillis)
    val creationTimeZone = event.creationTime.getZone
    if (!creationTimeZone.equals(EventValidation.defaultTimeZone)) {
      addStringToE(colNames("creationTimeZone"), creationTimeZone.getID)
    }

    // can use zero-length byte array for tag cell value
    (put, rowKey)
  }

  def resultToEvent(result: Result, appId: Int): Event = {
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

    def getLongCol(col: String): Long = {
      val r = result.getValue(eBytes, colNames(col))
      require(r != null,
        s"Failed to get value for column ${col}. " +
        s"Rowkey: ${rowKey.toString} " +
        s"StringBinary: ${Bytes.toStringBinary(result.getRow())}.")

      Bytes.toLong(r)
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
    val eventTime = new DateTime(
      getLongCol("eventTime"), eventTimeZone)
    val creationTimeZone = getOptStringCol("creationTimeZone")
      .map(DateTimeZone.forID(_))
      .getOrElse(EventValidation.defaultTimeZone)
    val creationTime: DateTime = new DateTime(
      getLongCol("creationTime"), creationTimeZone)

    Event(
      eventId = Some(RowKey(result.getRow()).toString),
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


  // for mandatory field. None means don't care.
  // for optional field. None means don't care.
  //    Some(None) means not exist.
  //    Some(Some(x)) means it should match x
  def createScan(
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None,
    entityType: Option[String] = None,
    entityId: Option[String] = None,
    eventNames: Option[Seq[String]] = None,
    targetEntityType: Option[Option[String]] = None,
    targetEntityId: Option[Option[String]] = None,
    reversed: Option[Boolean] = None): Scan = {

    val scan: Scan = new Scan()

    (entityType, entityId) match {
      case (Some(et), Some(eid)) => {
        val start = PartialRowKey(et, eid,
          startTime.map(_.getMillis)).toBytes
        // if no untilTime, stop when reach next bytes of entityTypeAndId
        val stop = PartialRowKey(et, eid,
          untilTime.map(_.getMillis).orElse(Some(-1))).toBytes

        if (reversed.getOrElse(false)) {
          // Reversed order.
          // If you specify a startRow and stopRow,
          // to scan in reverse, the startRow needs to be lexicographically
          // after the stopRow.
          scan.setStartRow(stop)
          scan.setStopRow(start)
          scan.setReversed(true)
        } else {
          scan.setStartRow(start)
          scan.setStopRow(stop)
        }
      }
      case (_, _) => {
        val minTime: Long = startTime.map(_.getMillis).getOrElse(0)
        val maxTime: Long = untilTime.map(_.getMillis).getOrElse(Long.MaxValue)
        scan.setTimeRange(minTime, maxTime)
        if (reversed.getOrElse(false)) {
          scan.setReversed(true)
        }
      }
    }

    val filters = new FilterList(FilterList.Operator.MUST_PASS_ALL)

    val eBytes = Bytes.toBytes("e")

    def createBinaryFilter(col: String, value: Array[Byte]): SingleColumnValueFilter = {
      val comp = new BinaryComparator(value)
      new SingleColumnValueFilter(
        eBytes, colNames(col), CompareOp.EQUAL, comp)
    }

    // skip the row if the column exists
    def createSkipRowIfColumnExistFilter(col: String): SkipFilter = {
      val comp = new BinaryComparator(colNames(col))
      val q = new QualifierFilter(CompareOp.NOT_EQUAL, comp)
      // filters an entire row if any of the Cell checks do not pass
      new SkipFilter(q)
    }

    entityType.foreach { et =>
      val compType = new BinaryComparator(Bytes.toBytes(et))
      val filterType = new SingleColumnValueFilter(
        eBytes, colNames("entityType"), CompareOp.EQUAL, compType)
      filters.addFilter(filterType)
    }

    entityId.foreach { eid =>
      val compId = new BinaryComparator(Bytes.toBytes(eid))
      val filterId = new SingleColumnValueFilter(
        eBytes, colNames("entityId"), CompareOp.EQUAL, compId)
      filters.addFilter(filterId)
    }

    eventNames.foreach { eventsList =>
      // match any of event in the eventsList
      val eventFilters = new FilterList(FilterList.Operator.MUST_PASS_ONE)
      eventsList.foreach { e =>
        val compEvent = new BinaryComparator(Bytes.toBytes(e))
        val filterEvent = new SingleColumnValueFilter(
          eBytes, colNames("event"), CompareOp.EQUAL, compEvent)
        eventFilters.addFilter(filterEvent)
      }
      if (!eventFilters.getFilters().isEmpty) {
        filters.addFilter(eventFilters)
      }
    }

    targetEntityType.foreach { tetOpt =>
      if (tetOpt.isEmpty) {
        val filter = createSkipRowIfColumnExistFilter("targetEntityType")
        filters.addFilter(filter)
      } else {
        tetOpt.foreach { tet =>
          val filter = createBinaryFilter(
            "targetEntityType", Bytes.toBytes(tet))
          // the entire row will be skipped if the column is not found.
          filter.setFilterIfMissing(true)
          filters.addFilter(filter)
        }
      }
    }

    targetEntityId.foreach { teidOpt =>
      if (teidOpt.isEmpty) {
        val filter = createSkipRowIfColumnExistFilter("targetEntityId")
        filters.addFilter(filter)
      } else {
        teidOpt.foreach { teid =>
          val filter = createBinaryFilter(
            "targetEntityId", Bytes.toBytes(teid))
          // the entire row will be skipped if the column is not found.
          filter.setFilterIfMissing(true)
          filters.addFilter(filter)
        }
      }
    }

    if (!filters.getFilters().isEmpty) {
      scan.setFilter(filters)
    }

    scan
  }

}
