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

package org.apache.predictionio.data.storage

import grizzled.slf4j.Logger
import org.apache.predictionio.annotation.DeveloperApi
import org.apache.predictionio.annotation.Experimental
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.joda.time.DateTime

import scala.reflect.ClassTag

/** :: DeveloperApi ::
  * Base trait of a data access object that returns [[Event]] related RDD data
  * structure. Engine developers should use
  * [[org.apache.predictionio.data.store.PEventStore]] instead of using this directly.
  *
  * @group Event Data
  */
@DeveloperApi
trait PEvents extends Serializable {
  @transient protected lazy val logger = Logger[this.type]
  @deprecated("Use PEventStore.find() instead.", "0.9.2")
  def getByAppIdAndTimeAndEntity(appId: Int,
    startTime: Option[DateTime],
    untilTime: Option[DateTime],
    entityType: Option[String],
    entityId: Option[String])(sc: SparkContext): RDD[Event] = {
      find(
        appId = appId,
        startTime = startTime,
        untilTime = untilTime,
        entityType = entityType,
        entityId = entityId,
        eventNames = None
      )(sc)
    }

  /** :: DeveloperApi ::
    * Read from database and return the events. The deprecation here is intended
    * to engine developers only.
    *
    * @param appId return events of this app ID
    * @param channelId return events of this channel ID (default channel if it's None)
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
  @deprecated("Use PEventStore.find() instead.", "0.9.2")
  @DeveloperApi
  def find(
    appId: Int,
    channelId: Option[Int] = None,
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None,
    entityType: Option[String] = None,
    entityId: Option[String] = None,
    eventNames: Option[Seq[String]] = None,
    targetEntityType: Option[Option[String]] = None,
    targetEntityId: Option[Option[String]] = None)(sc: SparkContext): RDD[Event]

  /** Aggregate properties of entities based on these special events:
    * \$set, \$unset, \$delete events. The deprecation here is intended to
    * engine developers only.
    *
    * @param appId use events of this app ID
    * @param channelId use events of this channel ID (default channel if it's None)
    * @param entityType aggregate properties of the entities of this entityType
    * @param startTime use events with eventTime >= startTime
    * @param untilTime use events with eventTime < untilTime
    * @param required only keep entities with these required properties defined
    * @param sc Spark context
    * @return RDD[(String, PropertyMap)] RDD of entityId and PropertyMap pair
    */
  @deprecated("Use PEventStore.aggregateProperties() instead.", "0.9.2")
  def aggregateProperties(
    appId: Int,
    channelId: Option[Int] = None,
    entityType: String,
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None,
    required: Option[Seq[String]] = None)
    (sc: SparkContext): RDD[(String, PropertyMap)] = {
    val eventRDD = find(
      appId = appId,
      channelId = channelId,
      startTime = startTime,
      untilTime = untilTime,
      entityType = Some(entityType),
      eventNames = Some(PEventAggregator.eventNames))(sc)

    val dmRDD = PEventAggregator.aggregateProperties(eventRDD)

    required map { r =>
      dmRDD.filter { case (k, v) =>
        r.map(v.contains(_)).reduce(_ && _)
      }
    } getOrElse dmRDD
  }

  /** :: Experimental ::
    * Extract EntityMap[A] from events for the entityType
    * NOTE: it is local EntityMap[A]
    */
  @deprecated("Use PEventStore.aggregateProperties() instead.", "0.9.2")
  @Experimental
  def extractEntityMap[A: ClassTag](
    appId: Int,
    entityType: String,
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None,
    required: Option[Seq[String]] = None)
    (sc: SparkContext)(extract: DataMap => A): EntityMap[A] = {
    val idToData: Map[String, A] = aggregateProperties(
      appId = appId,
      entityType = entityType,
      startTime = startTime,
      untilTime = untilTime,
      required = required
    )(sc).map{ case (id, dm) =>
      try {
        (id, extract(dm))
      } catch {
        case e: Exception => {
          logger.error(s"Failed to get extract entity from DataMap $dm of " +
            s"entityId $id.", e)
          throw e
        }
      }
    }.collectAsMap.toMap

    new EntityMap(idToData)
  }

  /** :: DeveloperApi ::
    * Write events to database
    *
    * @param events RDD of Event
    * @param appId the app ID
    * @param sc Spark Context
    */
  @DeveloperApi
  def write(events: RDD[Event], appId: Int)(sc: SparkContext): Unit =
    write(events, appId, None)(sc)

  /** :: DeveloperApi ::
    * Write events to database
    *
    * @param events RDD of Event
    * @param appId the app ID
    * @param channelId  channel ID (default channel if it's None)
    * @param sc Spark Context
    */
  @DeveloperApi
  def write(events: RDD[Event], appId: Int, channelId: Option[Int])(sc: SparkContext): Unit
}
