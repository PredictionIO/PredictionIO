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

import org.apache.predictionio.annotation.DeveloperApi
import org.apache.predictionio.annotation.Experimental

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext
import scala.concurrent.TimeoutException

import org.joda.time.DateTime

/** :: DeveloperApi ::
  * Base trait of a data access object that directly returns [[Event]] without
  * going through Spark's parallelization. Engine developers should use
  * [[org.apache.predictionio.data.store.LEventStore]] instead of using this directly.
  *
  * @group Event Data
  */
@DeveloperApi
trait LEvents {
  /** Default timeout for asynchronous operations that is set to 1 minute */
  val defaultTimeout = Duration(60, "seconds")

  /** :: DeveloperApi ::
    * Initialize Event Store for an app ID and optionally a channel ID.
    * This routine is to be called when an app is first created.
    *
    * @param appId App ID
    * @param channelId Optional channel ID
    * @return true if initialization was successful; false otherwise.
    */
  @DeveloperApi
  def init(appId: Int, channelId: Option[Int] = None): Boolean

  /** :: DeveloperApi ::
    * Remove Event Store for an app ID and optional channel ID.
    *
    * @param appId App ID
    * @param channelId Optional channel ID
    * @return true if removal was successful; false otherwise.
    */
  @DeveloperApi
  def remove(appId: Int, channelId: Option[Int] = None): Boolean

  /** :: DeveloperApi ::
    * Close this Event Store interface object, e.g. close connection, release
    * resources, etc.
    */
  @DeveloperApi
  def close(): Unit

  /** :: DeveloperApi ::
    * Insert an [[Event]] in a non-blocking fashion.
    *
    * @param event An [[Event]] to be inserted
    * @param appId App ID for the [[Event]] to be inserted to
    */
  @DeveloperApi
  def futureInsert(event: Event, appId: Int)(implicit ec: ExecutionContext):
    Future[String] = futureInsert(event, appId, None)

  /** :: DeveloperApi ::
    * Insert an [[Event]] in a non-blocking fashion.
    *
    * @param event An [[Event]] to be inserted
    * @param appId App ID for the [[Event]] to be inserted to
    * @param channelId Optional channel ID for the [[Event]] to be inserted to
    */
  @DeveloperApi
  def futureInsert(
    event: Event, appId: Int, channelId: Option[Int])(implicit ec: ExecutionContext): Future[String]

  /** :: DeveloperApi ::
    * Get an [[Event]] in a non-blocking fashion.
    *
    * @param eventId ID of the [[Event]]
    * @param appId ID of the app that contains the [[Event]]
    */
  @DeveloperApi
  def futureGet(eventId: String, appId: Int)(implicit ec: ExecutionContext):
    Future[Option[Event]] = futureGet(eventId, appId, None)

  /** :: DeveloperApi ::
    * Get an [[Event]] in a non-blocking fashion.
    *
    * @param eventId ID of the [[Event]]
    * @param appId ID of the app that contains the [[Event]]
    * @param channelId Optional channel ID that contains the [[Event]]
    */
  @DeveloperApi
  def futureGet(
      eventId: String,
      appId: Int,
      channelId: Option[Int]
    )(implicit ec: ExecutionContext): Future[Option[Event]]

  /** :: DeveloperApi ::
    * Delete an [[Event]] in a non-blocking fashion.
    *
    * @param eventId ID of the [[Event]]
    * @param appId ID of the app that contains the [[Event]]
    */
  @DeveloperApi
  def futureDelete(eventId: String, appId: Int)(implicit ec: ExecutionContext):
    Future[Boolean] = futureDelete(eventId, appId, None)

  /** :: DeveloperApi ::
    * Delete an [[Event]] in a non-blocking fashion.
    *
    * @param eventId ID of the [[Event]]
    * @param appId ID of the app that contains the [[Event]]
    * @param channelId Optional channel ID that contains the [[Event]]
    */
  @DeveloperApi
  def futureDelete(
      eventId: String,
      appId: Int,
      channelId: Option[Int]
    )(implicit ec: ExecutionContext): Future[Boolean]

  /** :: DeveloperApi ::
    * Reads from database and returns a Future of Iterator of [[Event]]s.
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
    * @param limit Limit number of events. Get all events if None or Some(-1)
    * @param reversed Reverse the order.
    *   - return oldest events first if None or Some(false) (default)
    *   - return latest events first if Some(true)
    * @param ec ExecutionContext
    * @return Future[Iterator[Event]]
    */
  @DeveloperApi
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
    )(implicit ec: ExecutionContext): Future[Iterator[Event]]

  /** Aggregate properties of entities based on these special events:
    * \$set, \$unset, \$delete events.
    * and returns a Future of Map of entityId to properties.
    *
    * @param appId use events of this app ID
    * @param channelId use events of this channel ID (default channel if it's None)
    * @param entityType aggregate properties of the entities of this entityType
    * @param startTime use events with eventTime >= startTime
    * @param untilTime use events with eventTime < untilTime
    * @param required only keep entities with these required properties defined
    * @param ec ExecutionContext
    * @return Future[Map[String, PropertyMap]]
    */
  private[predictionio] def futureAggregateProperties(
    appId: Int,
    channelId: Option[Int] = None,
    entityType: String,
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None,
    required: Option[Seq[String]] = None)(implicit ec: ExecutionContext):
    Future[Map[String, PropertyMap]] = {
      futureFind(
        appId = appId,
        channelId = channelId,
        startTime = startTime,
        untilTime = untilTime,
        entityType = Some(entityType),
        eventNames = Some(LEventAggregator.eventNames)
      ).map{ eventIt =>
        val dm = LEventAggregator.aggregateProperties(eventIt)
        if (required.isDefined) {
          dm.filter { case (k, v) =>
            required.get.map(v.contains(_)).reduce(_ && _)
          }
        } else dm
      }
    }

  /**
    * :: Experimental ::
    *
    * Aggregate properties of the specified entity (entityType + entityId)
    * based on these special events:
    * \$set, \$unset, \$delete events.
    * and returns a Future of Option[PropertyMap]
    *
    * @param appId use events of this app ID
    * @param channelId use events of this channel ID (default channel if it's None)
    * @param entityType the entityType
    * @param entityId the entityId
    * @param startTime use events with eventTime >= startTime
    * @param untilTime use events with eventTime < untilTime
    * @param ec ExecutionContext
    * @return Future[Option[PropertyMap]]
    */
  @Experimental
  private[predictionio] def futureAggregatePropertiesOfEntity(
    appId: Int,
    channelId: Option[Int] = None,
    entityType: String,
    entityId: String,
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None)(implicit ec: ExecutionContext):
    Future[Option[PropertyMap]] = {
      futureFind(
        appId = appId,
        channelId = channelId,
        startTime = startTime,
        untilTime = untilTime,
        entityType = Some(entityType),
        entityId = Some(entityId),
        eventNames = Some(LEventAggregator.eventNames)
      ).map{ eventIt =>
        LEventAggregator.aggregatePropertiesSingle(eventIt)
      }
    }

  // following is blocking
  private[predictionio] def insert(event: Event, appId: Int,
    channelId: Option[Int] = None,
    timeout: Duration = defaultTimeout)(implicit ec: ExecutionContext):
    String = {
    Await.result(futureInsert(event, appId, channelId), timeout)
  }

  private[predictionio] def get(eventId: String, appId: Int,
    channelId: Option[Int] = None,
    timeout: Duration = defaultTimeout)(implicit ec: ExecutionContext):
    Option[Event] = {
    Await.result(futureGet(eventId, appId, channelId), timeout)
  }

  private[predictionio] def delete(eventId: String, appId: Int,
    channelId: Option[Int] = None,
    timeout: Duration = defaultTimeout)(implicit ec: ExecutionContext):
    Boolean = {
    Await.result(futureDelete(eventId, appId, channelId), timeout)
  }

  /** reads from database and returns events iterator.
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
    * @param limit Limit number of events. Get all events if None or Some(-1)
    * @param reversed Reverse the order (should be used with both
    *   targetEntityType and targetEntityId specified)
    *   - return oldest events first if None or Some(false) (default)
    *   - return latest events first if Some(true)
    * @param ec ExecutionContext
    * @return Iterator[Event]
    */
  private[predictionio] def find(
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
    reversed: Option[Boolean] = None,
    timeout: Duration = defaultTimeout)(implicit ec: ExecutionContext):
    Iterator[Event] = {
      Await.result(futureFind(
        appId = appId,
        channelId = channelId,
        startTime = startTime,
        untilTime = untilTime,
        entityType = entityType,
        entityId = entityId,
        eventNames = eventNames,
        targetEntityType = targetEntityType,
        targetEntityId = targetEntityId,
        limit = limit,
        reversed = reversed), timeout)
  }

  // NOTE: remove in next release
  @deprecated("Use find() instead.", "0.9.2")
  private[predictionio] def findLegacy(
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
    reversed: Option[Boolean] = None,
    timeout: Duration = defaultTimeout)(implicit ec: ExecutionContext):
    Either[StorageError, Iterator[Event]] = {
      try {
        // return Either for legacy usage
        Right(Await.result(futureFind(
          appId = appId,
          channelId = channelId,
          startTime = startTime,
          untilTime = untilTime,
          entityType = entityType,
          entityId = entityId,
          eventNames = eventNames,
          targetEntityType = targetEntityType,
          targetEntityId = targetEntityId,
          limit = limit,
          reversed = reversed), timeout))
      } catch {
        case e: TimeoutException => Left(StorageError(s"${e}"))
        case e: Exception => Left(StorageError(s"${e}"))
      }
  }

  /** reads events of the specified entity.
    *
    * @param appId return events of this app ID
    * @param channelId return events of this channel ID (default channel if it's None)
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
    * @param startTime return events with eventTime >= startTime
    * @param untilTime return events with eventTime < untilTime
    * @param limit Limit number of events. Get all events if None or Some(-1)
    * @param latest Return latest event first (default true)
    * @param ec ExecutionContext
    * @return Either[StorageError, Iterator[Event]]
    */
  // NOTE: remove this function in next release
  @deprecated("Use LEventStore.findByEntity() instead.", "0.9.2")
  def findSingleEntity(
    appId: Int,
    channelId: Option[Int] = None,
    entityType: String,
    entityId: String,
    eventNames: Option[Seq[String]] = None,
    targetEntityType: Option[Option[String]] = None,
    targetEntityId: Option[Option[String]] = None,
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None,
    limit: Option[Int] = None,
    latest: Boolean = true,
    timeout: Duration = defaultTimeout)(implicit ec: ExecutionContext):
    Either[StorageError, Iterator[Event]] = {

    findLegacy(
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
      reversed = Some(latest),
      timeout = timeout)

  }

  /** Aggregate properties of entities based on these special events:
    * \$set, \$unset, \$delete events.
    * and returns a Map of entityId to properties.
    *
    * @param appId use events of this app ID
    * @param channelId use events of this channel ID (default channel if it's None)
    * @param entityType aggregate properties of the entities of this entityType
    * @param startTime use events with eventTime >= startTime
    * @param untilTime use events with eventTime < untilTime
    * @param required only keep entities with these required properties defined
    * @param ec ExecutionContext
    * @return Map[String, PropertyMap]
    */
  private[predictionio] def aggregateProperties(
    appId: Int,
    channelId: Option[Int] = None,
    entityType: String,
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None,
    required: Option[Seq[String]] = None,
    timeout: Duration = defaultTimeout)(implicit ec: ExecutionContext):
    Map[String, PropertyMap] = {
    Await.result(futureAggregateProperties(
      appId = appId,
      channelId = channelId,
      entityType = entityType,
      startTime = startTime,
      untilTime = untilTime,
      required = required), timeout)
  }

  /**
    * :: Experimental ::
    *
    * Aggregate properties of the specified entity (entityType + entityId)
    * based on these special events:
    * \$set, \$unset, \$delete events.
    * and returns Option[PropertyMap]
    *
    * @param appId use events of this app ID
    * @param channelId use events of this channel ID
    * @param entityType the entityType
    * @param entityId the entityId
    * @param startTime use events with eventTime >= startTime
    * @param untilTime use events with eventTime < untilTime
    * @param ec ExecutionContext
    * @return Future[Option[PropertyMap]]
    */
  @Experimental
  private[predictionio] def aggregatePropertiesOfEntity(
    appId: Int,
    channelId: Option[Int] = None,
    entityType: String,
    entityId: String,
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None,
    timeout: Duration = defaultTimeout)(implicit ec: ExecutionContext):
    Option[PropertyMap] = {

    Await.result(futureAggregatePropertiesOfEntity(
      appId = appId,
      channelId = channelId,
      entityType = entityType,
      entityId = entityId,
      startTime = startTime,
      untilTime = untilTime), timeout)
  }

}
