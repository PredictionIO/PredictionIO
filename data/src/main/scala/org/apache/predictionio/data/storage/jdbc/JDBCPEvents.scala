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

package org.apache.predictionio.data.storage.jdbc

import java.sql.{DriverManager, ResultSet}

import com.github.nscala_time.time.Imports._
import org.apache.predictionio.data.storage.{DataMap, Event, PEvents, StorageClientConfig}
import org.apache.spark.SparkContext
import org.apache.spark.rdd.{JdbcRDD, RDD}
import org.apache.spark.sql.{SQLContext, SaveMode}
import org.json4s.JObject
import org.json4s.native.Serialization

/** JDBC implementation of [[PEvents]] */
class JDBCPEvents(client: String, config: StorageClientConfig, namespace: String) extends PEvents {
  @transient private implicit lazy val formats = org.json4s.DefaultFormats
  def find(
    appId: Int,
    channelId: Option[Int] = None,
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None,
    entityType: Option[String] = None,
    entityId: Option[String] = None,
    eventNames: Option[Seq[String]] = None,
    targetEntityType: Option[Option[String]] = None,
    targetEntityId: Option[Option[String]] = None)(sc: SparkContext): RDD[Event] = {
    val lower = startTime.map(_.getMillis).getOrElse(0.toLong)
    /** Change the default upper bound from +100 to +1 year because MySQL's
      * FROM_UNIXTIME(t) will return NULL if we use +100 years.
      */
    val upper = untilTime.map(_.getMillis).getOrElse((DateTime.now + 1.years).getMillis)
    val par = scala.math.min(
      new Duration(upper - lower).getStandardDays,
      config.properties.getOrElse("PARTITIONS", "4").toLong).toInt
    val entityTypeClause = entityType.map(x => s"and entityType = '$x'").getOrElse("")
    val entityIdClause = entityId.map(x => s"and entityId = '$x'").getOrElse("")
    val eventNamesClause =
      eventNames.map("and (" + _.map(y => s"event = '$y'").mkString(" or ") + ")").getOrElse("")
    val targetEntityTypeClause = targetEntityType.map(
      _.map(x => s"and targetEntityType = '$x'"
    ).getOrElse("and targetEntityType is null")).getOrElse("")
    val targetEntityIdClause = targetEntityId.map(
      _.map(x => s"and targetEntityId = '$x'"
    ).getOrElse("and targetEntityId is null")).getOrElse("")
    val q = s"""
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
      from ${JDBCUtils.eventTableName(namespace, appId, channelId)}
      where
        eventTime >= ${JDBCUtils.timestampFunction(client)}(?) and
        eventTime < ${JDBCUtils.timestampFunction(client)}(?)
      $entityTypeClause
      $entityIdClause
      $eventNamesClause
      $targetEntityTypeClause
      $targetEntityIdClause
      """.replace("\n", " ")
    new JdbcRDD(
      sc,
      () => {
        DriverManager.getConnection(
          client,
          config.properties("USERNAME"),
          config.properties("PASSWORD"))
      },
      q,
      lower / 1000,
      upper / 1000,
      par,
      (r: ResultSet) => {
        Event(
          eventId = Option(r.getString("id")),
          event = r.getString("event"),
          entityType = r.getString("entityType"),
          entityId = r.getString("entityId"),
          targetEntityType = Option(r.getString("targetEntityType")),
          targetEntityId = Option(r.getString("targetEntityId")),
          properties = Option(r.getString("properties")).map(x =>
            DataMap(Serialization.read[JObject](x))).getOrElse(DataMap()),
          eventTime = new DateTime(r.getTimestamp("eventTime").getTime,
            DateTimeZone.forID(r.getString("eventTimeZone"))),
          tags = Option(r.getString("tags")).map(x =>
            x.split(",").toList).getOrElse(Nil),
          prId = Option(r.getString("prId")),
          creationTime = new DateTime(r.getTimestamp("creationTime").getTime,
            DateTimeZone.forID(r.getString("creationTimeZone"))))
      }).cache()
  }

  def write(events: RDD[Event], appId: Int, channelId: Option[Int])(sc: SparkContext): Unit = {
    val sqlContext = new SQLContext(sc)

    import sqlContext.implicits._

    val tableName = JDBCUtils.eventTableName(namespace, appId, channelId)

    val eventTableColumns = Seq[String](
        "id"
      , "event"
      , "entityType"
      , "entityId"
      , "targetEntityType"
      , "targetEntityId"
      , "properties"
      , "eventTime"
      , "eventTimeZone"
      , "tags"
      , "prId"
      , "creationTime"
      , "creationTimeZone")

    val eventDF = events.map { event =>
      (event.eventId.getOrElse(JDBCUtils.generateId)
        , event.event
        , event.entityType
        , event.entityId
        , event.targetEntityType.orNull
        , event.targetEntityId.orNull
        , if (!event.properties.isEmpty) Serialization.write(event.properties.toJObject) else null
        , new java.sql.Timestamp(event.eventTime.getMillis)
        , event.eventTime.getZone.getID
        , if (event.tags.nonEmpty) Some(event.tags.mkString(",")) else null
        , event.prId
        , new java.sql.Timestamp(event.creationTime.getMillis)
        , event.creationTime.getZone.getID)
    }.toDF(eventTableColumns:_*)

    // spark version 1.4.0 or higher
    val prop = new java.util.Properties
    prop.setProperty("user", config.properties("USERNAME"))
    prop.setProperty("password", config.properties("PASSWORD"))
    eventDF.write.mode(SaveMode.Append).jdbc(client, tableName, prop)
  }
}
