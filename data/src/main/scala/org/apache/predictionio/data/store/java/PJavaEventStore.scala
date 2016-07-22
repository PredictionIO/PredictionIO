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

package org.apache.predictionio.data.store.java

import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.PropertyMap
import org.apache.predictionio.data.store.PEventStore
import org.apache.spark.SparkContext
import org.apache.spark.api.java.JavaRDD
import org.joda.time.DateTime

import scala.collection.JavaConversions

/** This Java-friendly object provides a set of operation to access Event Store
  * with Spark's parallelization
  */
object PJavaEventStore {

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
    * @return JavaRDD[Event]
    */
  def find(
    appName: String,
    channelName: Option[String],
    startTime: Option[DateTime],
    untilTime: Option[DateTime],
    entityType: Option[String],
    entityId: Option[String],
    eventNames: Option[java.util.List[String]],
    targetEntityType: Option[Option[String]],
    targetEntityId: Option[Option[String]],
    sc: SparkContext): JavaRDD[Event] = {

    val eventNamesSeq = eventNames.map(JavaConversions.asScalaBuffer(_).toSeq)

    PEventStore.find(
      appName,
      channelName,
      startTime,
      untilTime,
      entityType,
      entityId,
      eventNamesSeq,
      targetEntityType,
      targetEntityId
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
    * @return JavaRDD[(String, PropertyMap)] JavaRDD of entityId and PropetyMap pair
    */
  def aggregateProperties(
    appName: String,
    entityType: String,
    channelName: Option[String],
    startTime: Option[DateTime],
    untilTime: Option[DateTime],
    required: Option[java.util.List[String]],
    sc: SparkContext): JavaRDD[(String, PropertyMap)] = {

    PEventStore.aggregateProperties(
      appName,
    entityType,
    channelName,
    startTime,
    untilTime
    )(sc)
  }

}
