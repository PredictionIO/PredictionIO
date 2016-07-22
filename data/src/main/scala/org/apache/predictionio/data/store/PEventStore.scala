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

package org.apache.predictionio.data.store

import org.apache.predictionio.data.storage.Storage
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.PropertyMap

import org.joda.time.DateTime

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

/** This object provides a set of operation to access Event Store
  * with Spark's parallelization
  */
object PEventStore {

  @transient lazy private val eventsDb = Storage.getPEvents()

  /** Read events from Event Store
    *
    * @param appName return events of this app
    * @param channelName return events of this channel (default channel if it's None)
    * @param startTime return events with eventTime >= startTime
    * @param untilTime return events with eventTime < untilTime
    * @param entityType return events of this entityType
    * @param entityId return events of this entityId
    * @param eventNames return events with any of these event names.
    * @param targetEntityType return events of this targetEntityType:
    *   - None means no restriction on targetEntityType
    *   - Some(None) means no targetEntityType for this event
    *   - Some(Some(x)) means targetEntityType should match x.
    * @param targetEntityId return events of this targetEntityId
    *   - None means no restriction on targetEntityId
    *   - Some(None) means no targetEntityId for this event
    *   - Some(Some(x)) means targetEntityId should match x.
    * @param sc Spark context
    * @return RDD[Event]
    */
  def find(
    appName: String,
    channelName: Option[String] = None,
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None,
    entityType: Option[String] = None,
    entityId: Option[String] = None,
    eventNames: Option[Seq[String]] = None,
    targetEntityType: Option[Option[String]] = None,
    targetEntityId: Option[Option[String]] = None
  )(sc: SparkContext): RDD[Event] = {

    val (appId, channelId) = Common.appNameToId(appName, channelName)

    eventsDb.find(
      appId = appId,
      channelId = channelId,
      startTime = startTime,
      untilTime = untilTime,
      entityType = entityType,
      entityId = entityId,
      eventNames = eventNames,
      targetEntityType = targetEntityType,
      targetEntityId = targetEntityId
    )(sc)

  }

  /** Aggregate properties of entities based on these special events:
    * \$set, \$unset, \$delete events.
    *
    * @param appName use events of this app
    * @param entityType aggregate properties of the entities of this entityType
    * @param channelName use events of this channel (default channel if it's None)
    * @param startTime use events with eventTime >= startTime
    * @param untilTime use events with eventTime < untilTime
    * @param required only keep entities with these required properties defined
    * @param sc Spark context
    * @return RDD[(String, PropertyMap)] RDD of entityId and PropetyMap pair
    */
  def aggregateProperties(
    appName: String,
    entityType: String,
    channelName: Option[String] = None,
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None,
    required: Option[Seq[String]] = None)
    (sc: SparkContext): RDD[(String, PropertyMap)] = {

      val (appId, channelId) = Common.appNameToId(appName, channelName)

      eventsDb.aggregateProperties(
        appId = appId,
        entityType = entityType,
        channelId = channelId,
        startTime = startTime,
        untilTime = untilTime,
        required = required
      )(sc)

    }

}
