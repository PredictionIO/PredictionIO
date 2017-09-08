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
package org.apache.predictionio.data.store.python

import java.sql.Timestamp

import org.apache.predictionio.data.store.PEventStore
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.joda.time.DateTime


/** This object provides a set of operation to access Event Store
  * with Spark's parallelization
  */
object PPythonEventStore {


  /** Read events from Event Store
    *
    * @param appName          return events of this app
    * @param channelName      return events of this channel (default channel if it's None)
    * @param startTime        return events with eventTime >= startTime
    * @param untilTime        return events with eventTime < untilTime
    * @param entityType       return events of this entityType
    * @param entityId         return events of this entityId
    * @param eventNames       return events with any of these event names.
    * @param targetEntityType return events of this targetEntityType:
    *   - None means no restriction on targetEntityType
    *   - Some(None) means no targetEntityType for this event
    *   - Some(Some(x)) means targetEntityType should match x.
    * @param targetEntityId   return events of this targetEntityId
    *   - None means no restriction on targetEntityId
    *   - Some(None) means no targetEntityId for this event
    *   - Some(Some(x)) means targetEntityId should match x.
    * @param spark            Spark context
    * @return DataFrame
    */
  def find(
            appName: String,
            channelName: String,
            startTime: Timestamp,
            untilTime: Timestamp,
            entityType: String,
            entityId: String,
            eventNames: Array[String],
            targetEntityType: String,
            targetEntityId: String
          )(spark: SparkSession): DataFrame = {
    import spark.implicits._
    val colNames: Seq[String] =
      Seq(
        "eventId",
        "event",
        "entityType",
        "entityId",
        "targetEntityType",
        "targetEntityId",
        "eventTime",
        "tags",
        "prId",
        "creationTime",
        "fields"
      )
    PEventStore.find(appName,
      Option(channelName),
      Option(startTime).map(t => new DateTime(t.getTime)),
      Option(untilTime).map(t => new DateTime(t.getTime)),
      Option(entityType),
      Option(entityId),
      Option(eventNames),
      Option(Option(targetEntityType)),
      Option(Option(targetEntityId)))(spark.sparkContext).map { e =>
      (
        e.eventId,
        e.event,
        e.entityType,
        e.entityId,
        e.targetEntityType.orNull,
        e.targetEntityId.orNull,
        new Timestamp(e.eventTime.getMillis),
        e.tags.mkString("\t"),
        e.prId.orNull,
        new Timestamp(e.creationTime.getMillis),
        e.properties.fields.mapValues(_.values.toString)
      )
    }.toDF(colNames: _*)
  }

  /** Aggregate properties of entities based on these special events:
    * \$set, \$unset, \$delete events.
    *
    * @param appName     use events of this app
    * @param entityType  aggregate properties of the entities of this entityType
    * @param channelName use events of this channel (default channel if it's None)
    * @param startTime   use events with eventTime >= startTime
    * @param untilTime   use events with eventTime < untilTime
    * @param required    only keep entities with these required properties defined
    * @param spark       Spark session
    * @return DataFrame  DataFrame of entityId and PropetyMap pair
    */
  def aggregateProperties(
                           appName: String,
                           entityType: String,
                           channelName: String,
                           startTime: Timestamp,
                           untilTime: Timestamp,
                           required: Array[String]
                         )
                         (spark: SparkSession): DataFrame = {
    import spark.implicits._
    val colNames: Seq[String] =
      Seq(
        "entityId",
        "firstUpdated",
        "lastUpdated",
        "fields"
      )
    PEventStore.aggregateProperties(appName,
      entityType,
      Option(channelName),
      Option(startTime).map(t => new DateTime(t.getTime)),
      Option(untilTime).map(t => new DateTime(t.getTime)),
      Option(required.toSeq))(spark.sparkContext).map { x =>
      val m = x._2
      (x._1,
        new Timestamp(m.firstUpdated.getMillis),
        new Timestamp(m.lastUpdated.getMillis),
        m.fields.mapValues(_.values.toString)
      )
    }.toDF(colNames: _*)
  }
}
