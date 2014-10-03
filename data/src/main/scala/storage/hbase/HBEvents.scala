/** Copyright 2014 TappingStone, Inc.
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

package io.prediction.data.storage.hbase

import io.prediction.data.storage.Event
import io.prediction.data.storage.EventID
import io.prediction.data.storage.EventValidation
import io.prediction.data.storage.Events
import io.prediction.data.storage.EventJson4sSupport
import io.prediction.data.storage.DataMap
import io.prediction.data.storage.StorageError
import io.prediction.data.storage.hbase.HBEventsUtil.RowKey
import io.prediction.data.storage.hbase.HBEventsUtil.RowKeyException
import io.prediction.data.storage.hbase.HBEventsUtil.PartialRowKey

import grizzled.slf4j.Logging

import org.json4s.DefaultFormats
import org.json4s.JObject
import org.json4s.native.Serialization.{ read, write }
//import org.json4s.ext.JodaTimeSerializers

import org.joda.time.DateTime
import org.joda.time.DateTimeZone

import org.apache.hadoop.hbase.NamespaceDescriptor
import org.apache.hadoop.hbase.NamespaceExistException
import org.apache.hadoop.hbase.HTableDescriptor
import org.apache.hadoop.hbase.HColumnDescriptor
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.HTable
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.filter.FilterList
import org.apache.hadoop.hbase.filter.RegexStringComparator
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp

import scala.collection.JavaConversions._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import java.util.UUID

//import org.apache.commons.codec.binary.Base64

class HBEvents(client: HBClient, namespace: String) extends Events with Logging {

  // check namespace exist
  val existingNamespace = client.admin.listNamespaceDescriptors().map(_.getName)
  if (!existingNamespace.contains(namespace)) {
    val nameDesc = NamespaceDescriptor.create(namespace).build()
    info(s"The namespace ${namespace} doesn't exist yet. Creating now...")
    client.admin.createNamespace(nameDesc)
  }
  /*
  try {
    val nameDesc = NamespaceDescriptor.create(namespace).build()
    client.admin.createNamespace(nameDesc)
  } catch {
    case e: NamespaceExistException => info(s"namespace already existed: ${e}")
    case e: Exception => throw new RuntimeException(e)
  }
  */

  implicit val formats = DefaultFormats + new EventJson4sSupport.DBSerializer

  val tableName = TableName.valueOf(namespace, HBEventsUtil.table)

  val colNames = HBEventsUtil.colNames

  def resultToEvent(result: Result): Event = HBEventsUtil.resultToEvent(result)

  // create table if not exist
  if (!client.admin.tableExists(tableName)) {
    val tableDesc = new HTableDescriptor(tableName)
    tableDesc.addFamily(new HColumnDescriptor("e"))
    tableDesc.addFamily(new HColumnDescriptor("r")) // reserved
    client.admin.createTable(tableDesc)
  }


  override
  def futureInsert(event: Event)(implicit ec: ExecutionContext):
    Future[Either[StorageError, String]] = {
    Future {
      //val table = new HTable(client.conf, tableName)
      val table = client.connection.getTable(tableName)
      val uuidLow: Long = UUID.randomUUID().getLeastSignificantBits
      val rowKey = new RowKey(
        appId = event.appId,
        millis = event.eventTime.getMillis,
        uuidLow = uuidLow
      )

      val eBytes = Bytes.toBytes("e")
      // use creationTime as HBase's cell timestamp
      val put = new Put(rowKey.toBytes, event.creationTime.getMillis)

      def addStringToE(col: Array[Byte], v: String) = {
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

      event.predictionKey.foreach { predictionKey =>
        addStringToE(colNames("predictionKey"), predictionKey)
      }

      val eventTimeZone = event.eventTime.getZone
      if (!eventTimeZone.equals(EventValidation.defaultTimeZone)) {
        addStringToE(colNames("eventTimeZone"), eventTimeZone.getID)
      }

      val creationTimeZone = event.creationTime.getZone
      if (!creationTimeZone.equals(EventValidation.defaultTimeZone)) {
        addStringToE(colNames("creationTimeZone"), creationTimeZone.getID)
      }

      // can use zero-length byte array for tag cell value

      table.put(put)
      table.flushCommits()
      table.close()
      Right(rowKey.toString)
    }/*.recover {
      case e: Exception => Left(StorageError(e.toString))
    }*/
  }



  override
  def futureGet(eventId: String)(implicit ec: ExecutionContext):
    Future[Either[StorageError, Option[Event]]] = {
      Future {
        val table = client.connection.getTable(tableName)
        val rowKey = RowKey(eventId)
        val get = new Get(rowKey.toBytes)

        val result = table.get(get)
        table.close()

        if (!result.isEmpty()) {
          val event = resultToEvent(result)
          Right(Some(event))
        } else {
          Right(None)
        }
      }.recover {
        case e: RowKeyException => Left(StorageError(e.toString))
        case e: Exception => throw e
      }
    }

  override
  def futureDelete(eventId: String)(implicit ec: ExecutionContext):
    Future[Either[StorageError, Boolean]] = {
    Future {
      val table = client.connection.getTable(tableName)
      val rowKey = RowKey(eventId)
      val exists = table.exists(new Get(rowKey.toBytes))
      table.delete(new Delete(rowKey.toBytes))
      table.close()
      Right(exists)
    }
  }

  override
  def futureGetByAppId(appId: Int)(implicit ec: ExecutionContext):
    Future[Either[StorageError, Iterator[Event]]] = {
      Future {
        val table = client.connection.getTable(tableName)
        val start = PartialRowKey(appId)
        val stop = PartialRowKey(appId+1)
        val scan = new Scan(start.toBytes, stop.toBytes)
        val scanner = table.getScanner(scan)
        table.close()
        Right(scanner.iterator().map { resultToEvent(_) })
      }
    }

  override
  def futureGetByAppIdAndTime(appId: Int, startTime: Option[DateTime],
    untilTime: Option[DateTime])(implicit ec: ExecutionContext):
    Future[Either[StorageError, Iterator[Event]]] = {
      Future {
        val table = client.connection.getTable(tableName)
        val start = PartialRowKey(appId, startTime.map(_.getMillis))
        // if no untilTime, stop when reach next appId
        val stop = untilTime.map(t => PartialRowKey(appId, Some(t.getMillis)))
          .getOrElse(PartialRowKey(appId+1))
        val scan = new Scan(start.toBytes, stop.toBytes)
        val scanner = table.getScanner(scan)
        table.close()
        Right(scanner.iterator().map { resultToEvent(_) })
      }
  }

  override
  def futureGetByAppIdAndTimeAndEntity(appId: Int,
    startTime: Option[DateTime],
    untilTime: Option[DateTime],
    entityType: Option[String],
    entityId: Option[String])(implicit ec: ExecutionContext):
    Future[Either[StorageError, Iterator[Event]]] = {
      Future {
        val table = client.connection.getTable(tableName)

        val scan = HBEventsUtil.createScan(appId, startTime, untilTime,
          entityType, entityId)
        val scanner = table.getScanner(scan)
        table.close()
        Right(scanner.iterator().map { resultToEvent(_) })
      }
  }

  override
  def futureDeleteByAppId(appId: Int)(implicit ec: ExecutionContext):
    Future[Either[StorageError, Unit]] = {
    Future {
      // TODO: better way to handle range delete
      val table = client.connection.getTable(tableName)
      val start = PartialRowKey(appId)
      val stop = PartialRowKey(appId+1)
      val scan = new Scan(start.toBytes, stop.toBytes)
      val scanner = table.getScanner(scan)
      val it = scanner.iterator()
      while (it.hasNext()) {
        val result = it.next()
        table.delete(new Delete(result.getRow()))
      }
      scanner.close()
      table.close()
      Right(())
    }
  }

}
