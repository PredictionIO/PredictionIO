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

import grizzled.slf4j.Logging
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.LEvents
import org.apache.predictionio.data.storage.StorageClientConfig
import org.apache.predictionio.data.storage.hbase.HBEventsUtil.RowKey
import org.apache.hadoop.hbase.HColumnDescriptor
import org.apache.hadoop.hbase.HTableDescriptor
import org.apache.hadoop.hbase.NamespaceDescriptor
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client._
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class HBLEvents(val client: HBClient, config: StorageClientConfig, val namespace: String)
  extends LEvents with Logging {

  // implicit val formats = DefaultFormats + new EventJson4sSupport.DBSerializer

  def resultToEvent(result: Result, appId: Int): Event =
    HBEventsUtil.resultToEvent(result, appId)

  def getTable(appId: Int, channelId: Option[Int] = None): HTableInterface =
    client.connection.getTable(HBEventsUtil.tableName(namespace, appId, channelId))

  override
  def init(appId: Int, channelId: Option[Int] = None): Boolean = {
    // check namespace exist
    val existingNamespace = client.admin.listNamespaceDescriptors()
      .map(_.getName)
    if (!existingNamespace.contains(namespace)) {
      val nameDesc = NamespaceDescriptor.create(namespace).build()
      info(s"The namespace ${namespace} doesn't exist yet. Creating now...")
      client.admin.createNamespace(nameDesc)
    }

    val tableName = TableName.valueOf(HBEventsUtil.tableName(namespace, appId, channelId))
    if (!client.admin.tableExists(tableName)) {
      info(s"The table ${tableName.getNameAsString()} doesn't exist yet." +
        " Creating now...")
      val tableDesc = new HTableDescriptor(tableName)
      tableDesc.addFamily(new HColumnDescriptor("e"))
      tableDesc.addFamily(new HColumnDescriptor("r")) // reserved
      client.admin.createTable(tableDesc)
    }
    true
  }

  override
  def remove(appId: Int, channelId: Option[Int] = None): Boolean = {
    val tableName = TableName.valueOf(HBEventsUtil.tableName(namespace, appId, channelId))
    try {
      if (client.admin.tableExists(tableName)) {
        info(s"Removing table ${tableName.getNameAsString()}...")
        client.admin.disableTable(tableName)
        client.admin.deleteTable(tableName)
      } else {
        info(s"Table ${tableName.getNameAsString()} doesn't exist." +
          s" Nothing is deleted.")
      }
      true
    } catch {
      case e: Exception => {
        error(s"Fail to remove table for appId ${appId}. Exception: ${e}")
        false
      }
    }
  }

  override
  def close(): Unit = {
    client.admin.close()
    client.connection.close()
  }

  override
  def futureInsert(
    event: Event, appId: Int, channelId: Option[Int])(implicit ec: ExecutionContext):
    Future[String] = {
    Future {
      val table = getTable(appId, channelId)
      val (put, rowKey) = HBEventsUtil.eventToPut(event, appId)
      table.put(put)
      table.flushCommits()
      table.close()
      rowKey.toString
    }
  }

  override
  def futureGet(
    eventId: String, appId: Int, channelId: Option[Int])(implicit ec: ExecutionContext):
    Future[Option[Event]] = {
      Future {
        val table = getTable(appId, channelId)
        val rowKey = RowKey(eventId)
        val get = new Get(rowKey.toBytes)

        val result = table.get(get)
        table.close()

        if (!result.isEmpty()) {
          val event = resultToEvent(result, appId)
          Some(event)
        } else {
          None
        }
      }
    }

  override
  def futureDelete(
    eventId: String, appId: Int, channelId: Option[Int])(implicit ec: ExecutionContext):
    Future[Boolean] = {
    Future {
      val table = getTable(appId, channelId)
      val rowKey = RowKey(eventId)
      val exists = table.exists(new Get(rowKey.toBytes))
      table.delete(new Delete(rowKey.toBytes))
      table.close()
      exists
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
    reversed: Option[Boolean] = None)(implicit ec: ExecutionContext):
    Future[Iterator[Event]] = {
      Future {

        require(!((reversed == Some(true)) && (entityType.isEmpty || entityId.isEmpty)),
          "the parameter reversed can only be used with both entityType and entityId specified.")

        val table = getTable(appId, channelId)

        val scan = HBEventsUtil.createScan(
          startTime = startTime,
          untilTime = untilTime,
          entityType = entityType,
          entityId = entityId,
          eventNames = eventNames,
          targetEntityType = targetEntityType,
          targetEntityId = targetEntityId,
          reversed = reversed)
        val scanner = table.getScanner(scan)
        table.close()

        val eventsIter = scanner.iterator()

        // Get all events if None or Some(-1)
        val results: Iterator[Result] = limit match {
          case Some(-1) => eventsIter
          case None => eventsIter
          case Some(x) => eventsIter.take(x)
        }

        val eventsIt = results.map { resultToEvent(_, appId) }

        eventsIt
      }
  }

}
