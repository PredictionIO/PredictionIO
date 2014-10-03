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

package io.prediction.data.storage

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.json4s.Formats

import scala.concurrent.ExecutionContext

import org.apache.commons.codec.binary.Base64

// separate Event for API before require mo
case class Event(
  val eventId: Option[EventID] = None,
  val event: String,
  val entityType: String,
  val entityId: String,
  val targetEntityType: Option[String] = None,
  val targetEntityId: Option[String] = None,
  val properties: DataMap = DataMap(), // default empty
  val eventTime: DateTime = DateTime.now,
  val tags: Seq[String] = Seq(),
  val appId: Int,
  val predictionKey: Option[String] = None,
  val creationTime: DateTime = DateTime.now
)

case class EventID(
  val bytes: Seq[Byte]
) {
  override
  def toString = Base64.encodeBase64URLSafeString(bytes.toArray)
}

object EventID {
  def apply(s: String) = new EventID(Base64.decodeBase64(s))
}

object EventValidation {

  val defaultTimeZone = DateTimeZone.UTC

  // general reserved prefix for built-in engine
  def isReservedPrefix(name: String): Boolean = name.startsWith("$") ||
    name.startsWith("pio_")

  // single entity reserved events
  val specialEvents = Set("$set", "$unset", "$delete")

  def isSpecialEvents(name: String): Boolean = specialEvents.contains(name)

  def validate(e: Event) = {

    require(e.appId >= 0, s"appId ${e.appId} cannot be negative.")
    require(!e.event.isEmpty, "event must not be empty.")
    require(!e.entityType.isEmpty, "entityType must not be empty string.")
    require(!e.entityId.isEmpty, "entityId must not be empty string.")
    require(e.targetEntityType.map(!_.isEmpty).getOrElse(true),
      "targetEntityType must not be empty string")
    require(e.targetEntityId.map(!_.isEmpty).getOrElse(true),
      "targetEntityId must not be empty string.")
    require(!((e.targetEntityType != None) && (e.targetEntityId == None)),
      "targetEntityType and targetEntityId must be specified together.")
    require(!((e.targetEntityType == None) && (e.targetEntityId != None)),
      "targetEntityType and targetEntityId must be specified together.")
    require(!((e.event == "$unset") && e.properties.isEmpty),
      "properties cannot be empty for $unset event")
    require(!isReservedPrefix(e.event) || isSpecialEvents(e.event),
      s"${e.event} is not a supported reserved event name.")
    require(!isSpecialEvents(e.event) ||
      ((e.targetEntityType == None) && (e.targetEntityId == None)),
      s"Reserved event ${e.event} cannot have targetEntity")
    require(!isReservedPrefix(e.entityType) ||
      isBuiltinEntityTypes(e.entityType),
      s"Reserved entityType ${e.entityType} is not supported.")
    require(e.targetEntityType.map{ t =>
      (!isReservedPrefix(t) || isBuiltinEntityTypes(t))}.getOrElse(true),
      s"Reserved targetEntityType ${e.targetEntityType} is not supported.")
    validateProperties(e)
  }

  // properties

  val builtinEntityTypes = Set("pio_user", "pio_item")
  val builtinProperties = Set(
    "pio_itypes", "pio_starttime", "pio_endtime",
    "pio_inactive", "pio_price", "pio_rating")

  def isBuiltinEntityTypes(name: String) = builtinEntityTypes.contains(name)

  def validateProperties(e: Event) = {
    e.properties.keySet.foreach { k =>
      require(!isReservedPrefix(k) || builtinProperties.contains(k),
        s"Reserved proeprties ${k} is not supported.")
    }
  }

}

trait Events {

  private def notImplemented(implicit ec: ExecutionContext) = Future {
    Left(StorageError("Not implemented."))
  }
  val timeout = Duration(5, "seconds")

  def futureInsert(event: Event)(implicit ec: ExecutionContext):
    Future[Either[StorageError, String]] =
    notImplemented

  def futureGet(eventId: String)(implicit ec: ExecutionContext):
    Future[Either[StorageError, Option[Event]]] =
    notImplemented

  def futureDelete(eventId: String)(implicit ec: ExecutionContext):
    Future[Either[StorageError, Boolean]] =
    notImplemented

  def futureGetByAppId(appId: Int)(implicit ec: ExecutionContext):
    Future[Either[StorageError, Iterator[Event]]] = notImplemented

  /* where t >= start and t < untilTime */
  def futureGetByAppIdAndTime(appId: Int, startTime: Option[DateTime],
    untilTime: Option[DateTime])(implicit ec: ExecutionContext):
    Future[Either[StorageError, Iterator[Event]]] = notImplemented

  def futureGetByAppIdAndTimeAndEntity(appId: Int,
    startTime: Option[DateTime],
    untilTime: Option[DateTime],
    entityType: Option[String],
    entityId: Option[String])(implicit ec: ExecutionContext):
    Future[Either[StorageError, Iterator[Event]]]  = notImplemented

  def futureDeleteByAppId(appId: Int)(implicit ec: ExecutionContext):
    Future[Either[StorageError, Unit]] = notImplemented

  // following is blocking
  def insert(event: Event)(implicit ec: ExecutionContext):
    Either[StorageError, String] = {
    Await.result(futureInsert(event), timeout)
  }

  def get(eventId: String)(implicit ec: ExecutionContext):
    Either[StorageError, Option[Event]] = {
    Await.result(futureGet(eventId), timeout)
  }

  def delete(eventId: String)(implicit ec: ExecutionContext):
    Either[StorageError, Boolean] = {
    Await.result(futureDelete(eventId), timeout)
  }

  def getByAppId(appId: Int)(implicit ec: ExecutionContext):
    Either[StorageError, Iterator[Event]] = {
    Await.result(futureGetByAppId(appId), timeout)
  }

  def getByAppIdAndTime(appId: Int, startTime: Option[DateTime],
    untilTime: Option[DateTime])(implicit ec: ExecutionContext):
    Either[StorageError, Iterator[Event]] = {
    Await.result(futureGetByAppIdAndTime(appId, startTime, untilTime), timeout)
  }

  def getByAppIdAndTimeAndEntity(appId: Int,
    startTime: Option[DateTime],
    untilTime: Option[DateTime],
    entityType: Option[String],
    entityId: Option[String])(implicit ec: ExecutionContext):
    Either[StorageError, Iterator[Event]] = {
    Await.result(futureGetByAppIdAndTimeAndEntity(appId, startTime, untilTime,
      entityType, entityId), timeout)
  }

  def deleteByAppId(appId: Int)(implicit ec: ExecutionContext):
    Either[StorageError, Unit] = {
    Await.result(futureDeleteByAppId(appId), timeout)
  }

}
