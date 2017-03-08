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


package org.apache.predictionio.data.storage.jdbc

import grizzled.slf4j.Logging
import org.apache.predictionio.data.storage.DataMap
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.LEvents
import org.apache.predictionio.data.storage.StorageClientConfig
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.json4s.JObject
import org.json4s.native.Serialization.read
import org.json4s.native.Serialization.write
import scalikejdbc._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/** JDBC implementation of [[LEvents]] */
class JDBCLEvents(
    client: String,
    config: StorageClientConfig,
    namespace: String) extends LEvents with Logging {
  implicit private val formats = org.json4s.DefaultFormats

  def init(appId: Int, channelId: Option[Int] = None): Boolean = {

    // To use index, it must be varchar less than 255 characters on a VARCHAR column
    val useIndex = config.properties.contains("INDEX") &&
      config.properties("INDEX").equalsIgnoreCase("enabled")

    val tableName = JDBCUtils.eventTableName(namespace, appId, channelId)
    val entityIdIndexName = s"idx_${tableName}_ei"
    val entityTypeIndexName = s"idx_${tableName}_et"
    DB autoCommit { implicit session =>
      if (useIndex) {
        SQL(s"""
      create table if not exists $tableName (
        id varchar(32) not null primary key,
        event varchar(255) not null,
        entityType varchar(255) not null,
        entityId varchar(255) not null,
        targetEntityType text,
        targetEntityId text,
        properties text,
        eventTime timestamp DEFAULT CURRENT_TIMESTAMP,
        eventTimeZone varchar(50) not null,
        tags text,
        prId text,
        creationTime timestamp DEFAULT CURRENT_TIMESTAMP,
        creationTimeZone varchar(50) not null)""").execute().apply()

        // create index
        SQL(s"create index $entityIdIndexName on $tableName (entityId)").execute().apply()
        SQL(s"create index $entityTypeIndexName on $tableName (entityType)").execute().apply()
      } else {
        SQL(s"""
      create table if not exists $tableName (
        id varchar(32) not null primary key,
        event text not null,
        entityType text not null,
        entityId text not null,
        targetEntityType text,
        targetEntityId text,
        properties text,
        eventTime timestamp DEFAULT CURRENT_TIMESTAMP,
        eventTimeZone varchar(50) not null,
        tags text,
        prId text,
        creationTime timestamp DEFAULT CURRENT_TIMESTAMP,
        creationTimeZone varchar(50) not null)""").execute().apply()
      }
      true
    }
  }

  def remove(appId: Int, channelId: Option[Int] = None): Boolean =
    DB autoCommit { implicit session =>
      SQL(s"""
      drop table ${JDBCUtils.eventTableName(namespace, appId, channelId)}
      """).execute().apply()
      true
    }

  def close(): Unit = ConnectionPool.closeAll()

  def futureInsert(event: Event, appId: Int, channelId: Option[Int])(
    implicit ec: ExecutionContext): Future[String] = Future {
    DB localTx { implicit session =>
      val id = event.eventId.getOrElse(JDBCUtils.generateId)
      val tableName = sqls.createUnsafely(JDBCUtils.eventTableName(namespace, appId, channelId))
      sql"""
      insert into $tableName values(
        $id,
        ${event.event},
        ${event.entityType},
        ${event.entityId},
        ${event.targetEntityType},
        ${event.targetEntityId},
        ${write(event.properties.toJObject)},
        ${event.eventTime},
        ${event.eventTime.getZone.getID},
        ${if (event.tags.nonEmpty) Some(event.tags.mkString(",")) else None},
        ${event.prId},
        ${event.creationTime},
        ${event.creationTime.getZone.getID}
      )
      """.update().apply()
      id
    }
  }

  def futureGet(eventId: String, appId: Int, channelId: Option[Int])(
    implicit ec: ExecutionContext): Future[Option[Event]] = Future {
    DB readOnly { implicit session =>
      val tableName = sqls.createUnsafely(JDBCUtils.eventTableName(namespace, appId, channelId))
      sql"""
      select
        id,
        event,
        entityType,
        entityId,
        targetEntityType,
        targetEntityId,
        properties,
        eventTime,
        eventTimeZone,
        tags,
        prId,
        creationTime,
        creationTimeZone
      from $tableName
      where id = $eventId
      """.map(resultToEvent).single().apply()
    }
  }

  def futureDelete(eventId: String, appId: Int, channelId: Option[Int])(
    implicit ec: ExecutionContext): Future[Boolean] = Future {
    DB localTx { implicit session =>
      val tableName = sqls.createUnsafely(JDBCUtils.eventTableName(namespace, appId, channelId))
      sql"""
      delete from $tableName where id = $eventId
      """.update().apply()
      true
    }
  }

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
    )(implicit ec: ExecutionContext): Future[Iterator[Event]] = Future {
    DB readOnly { implicit session =>
      val tableName = sqls.createUnsafely(JDBCUtils.eventTableName(namespace, appId, channelId))
      val whereClause = sqls.toAndConditionOpt(
        startTime.map(x => sqls"eventTime >= $x"),
        untilTime.map(x => sqls"eventTime < $x"),
        entityType.map(x => sqls"entityType = $x"),
        entityId.map(x => sqls"entityId = $x"),
        eventNames.map(x =>
          sqls.toOrConditionOpt(x.map(y =>
            Some(sqls"event = $y")
          ): _*)
        ).getOrElse(None),
        targetEntityType.map(x => x.map(y => sqls"targetEntityType = $y")
            .getOrElse(sqls"targetEntityType IS NULL")),
        targetEntityId.map(x => x.map(y => sqls"targetEntityId = $y")
            .getOrElse(sqls"targetEntityId IS NULL"))
      ).map(sqls.where(_)).getOrElse(sqls"")
      val orderByClause = reversed.map(x =>
        if (x) sqls"eventTime desc" else sqls"eventTime asc"
      ).getOrElse(sqls"eventTime asc")
      val limitClause = limit.map(x =>
        if (x < 0) sqls"" else sqls.limit(x)
      ).getOrElse(sqls"")
      val q = sql"""
      select
        id,
        event,
        entityType,
        entityId,
        targetEntityType,
        targetEntityId,
        properties,
        eventTime,
        eventTimeZone,
        tags,
        prId,
        creationTime,
        creationTimeZone
      from $tableName
      $whereClause
      order by $orderByClause
      $limitClause
      """
      q.map(resultToEvent).list().apply().toIterator
    }
  }

  private[predictionio] def resultToEvent(rs: WrappedResultSet): Event = {
    Event(
      eventId = rs.stringOpt("id"),
      event = rs.string("event"),
      entityType = rs.string("entityType"),
      entityId = rs.string("entityId"),
      targetEntityType = rs.stringOpt("targetEntityType"),
      targetEntityId = rs.stringOpt("targetEntityId"),
      properties = rs.stringOpt("properties").map(p =>
        DataMap(read[JObject](p))).getOrElse(DataMap()),
      eventTime = new DateTime(rs.jodaDateTime("eventTime"),
        DateTimeZone.forID(rs.string("eventTimeZone"))),
      tags = rs.stringOpt("tags").map(t => t.split(",").toList).getOrElse(Nil),
      prId = rs.stringOpt("prId"),
      creationTime = new DateTime(rs.jodaDateTime("creationTime"),
        DateTimeZone.forID(rs.string("creationTimeZone")))
    )
  }
}
