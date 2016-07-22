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

import org.joda.time.DateTime

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

/** This object provides a set of operation to access Event Store
  * without going through Spark's parallelization
  */
object LEventStore {

  private val defaultTimeout = Duration(60, "seconds")

  @transient lazy private val eventsDb = Storage.getLEvents()

  /** Reads events of the specified entity. May use this in Algorithm's predict()
    * or Serving logic to have fast event store access.
    *
    * @param appName return events of this app
    * @param entityType return events of this entityType
    * @param entityId return events of this entityId
    * @param channelName return events of this channel (default channel if it's None)
    * @param eventNames return events with any of these event names.
    * @param targetEntityType return events of this targetEntityType:
    *   - None means no restriction on targetEntityType
    *   - Some(None) means no targetEntityType for this event
    *   - Some(Some(x)) means targetEntityType should match x.
    * @param targetEntityId return events of this targetEntityId
    *   - None means no restriction on targetEntityId
    *   - Some(None) means no targetEntityId for this event
    *   - Some(Some(x)) means targetEntityId should match x.
    * @param startTime return events with eventTime >= startTime
    * @param untilTime return events with eventTime < untilTime
    * @param limit Limit number of events. Get all events if None or Some(-1)
    * @param latest Return latest event first (default true)
    * @return Iterator[Event]
    */
  def findByEntity(
    appName: String,
    entityType: String,
    entityId: String,
    channelName: Option[String] = None,
    eventNames: Option[Seq[String]] = None,
    targetEntityType: Option[Option[String]] = None,
    targetEntityId: Option[Option[String]] = None,
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None,
    limit: Option[Int] = None,
    latest: Boolean = true,
    timeout: Duration = defaultTimeout): Iterator[Event] = {

    val (appId, channelId) = Common.appNameToId(appName, channelName)

    Await.result(eventsDb.futureFind(
      appId = appId,
      channelId = channelId,
      startTime = startTime,
      untilTime = untilTime,
      entityType = Some(entityType),
      entityId = Some(entityId),
      eventNames = eventNames,
      targetEntityType = targetEntityType,
      targetEntityId = targetEntityId,
      limit = limit,
      reversed = Some(latest)),
      timeout)
  }

  /** Reads events generically. If entityType or entityId is not specified, it
    * results in table scan.
    *
    * @param appName return events of this app
    * @param entityType return events of this entityType
    *   - None means no restriction on entityType
    *   - Some(x) means entityType should match x.
    * @param entityId return events of this entityId
    *   - None means no restriction on entityId
    *   - Some(x) means entityId should match x.
    * @param channelName return events of this channel (default channel if it's None)
    * @param eventNames return events with any of these event names.
    * @param targetEntityType return events of this targetEntityType:
    *   - None means no restriction on targetEntityType
    *   - Some(None) means no targetEntityType for this event
    *   - Some(Some(x)) means targetEntityType should match x.
    * @param targetEntityId return events of this targetEntityId
    *   - None means no restriction on targetEntityId
    *   - Some(None) means no targetEntityId for this event
    *   - Some(Some(x)) means targetEntityId should match x.
    * @param startTime return events with eventTime >= startTime
    * @param untilTime return events with eventTime < untilTime
    * @param limit Limit number of events. Get all events if None or Some(-1)
    * @return Iterator[Event]
    */
  def find(
    appName: String,
    entityType: Option[String] = None,
    entityId: Option[String] = None,
    channelName: Option[String] = None,
    eventNames: Option[Seq[String]] = None,
    targetEntityType: Option[Option[String]] = None,
    targetEntityId: Option[Option[String]] = None,
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None,
    limit: Option[Int] = None,
    timeout: Duration = defaultTimeout): Iterator[Event] = {

    val (appId, channelId) = Common.appNameToId(appName, channelName)

    Await.result(eventsDb.futureFind(
      appId = appId,
      channelId = channelId,
      startTime = startTime,
      untilTime = untilTime,
      entityType = entityType,
      entityId = entityId,
      eventNames = eventNames,
      targetEntityType = targetEntityType,
      targetEntityId = targetEntityId,
      limit = limit), timeout)
  }

}
