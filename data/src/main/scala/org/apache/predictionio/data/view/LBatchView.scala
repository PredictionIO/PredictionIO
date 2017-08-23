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


package org.apache.predictionio.data.view

import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.EventValidation
import org.apache.predictionio.data.storage.DataMap
import org.apache.predictionio.data.storage.Storage

import org.joda.time.DateTime
import scala.language.implicitConversions

import scala.concurrent.ExecutionContext.Implicits.global // TODO

object ViewPredicates {
  @deprecated("Use LEvents or LEventStore instead.", "0.9.2")
  def getStartTimePredicate(startTimeOpt: Option[DateTime])
  : (Event => Boolean) = {
    startTimeOpt.map(getStartTimePredicate).getOrElse(_ => true)
  }

  @deprecated("Use LEvents or LEventStore instead.", "0.9.2")
  def getStartTimePredicate(startTime: DateTime): (Event => Boolean) = {
    e => (!(e.eventTime.isBefore(startTime) || e.eventTime.isEqual(startTime)))
  }

  @deprecated("Use LEvents or LEventStore instead.", "0.9.2")
  def getUntilTimePredicate(untilTimeOpt: Option[DateTime])
  : (Event => Boolean) = {
    untilTimeOpt.map(getUntilTimePredicate).getOrElse(_ => true)
  }

  @deprecated("Use LEvents or LEventStore instead.", "0.9.2")
  def getUntilTimePredicate(untilTime: DateTime): (Event => Boolean) = {
    _.eventTime.isBefore(untilTime)
  }

  @deprecated("Use LEvents or LEventStore instead.", "0.9.2")
  def getEntityTypePredicate(entityTypeOpt: Option[String]): (Event => Boolean)
  = {
    entityTypeOpt.map(getEntityTypePredicate).getOrElse(_ => true)
  }

  @deprecated("Use LEvents or LEventStore instead.", "0.9.2")
  def getEntityTypePredicate(entityType: String): (Event => Boolean) = {
    (_.entityType == entityType)
  }

  @deprecated("Use LEvents or LEventStore instead.", "0.9.2")
  def getEventPredicate(eventOpt: Option[String]): (Event => Boolean)
  = {
    eventOpt.map(getEventPredicate).getOrElse(_ => true)
  }

  @deprecated("Use LEvents or LEventStore instead.", "0.9.2")
  def getEventPredicate(event: String): (Event => Boolean) = {
    (_.event == event)
  }
}

object ViewAggregators {
  @deprecated("Use LEvents instead.", "0.9.2")
  def getDataMapAggregator(): ((Option[DataMap], Event) => Option[DataMap]) = {
    (p, e) => {
      e.event match {
        case "$set" => {
          if (p == None) {
            Some(e.properties)
          } else {
            p.map(_ ++ e.properties)
          }
        }
        case "$unset" => {
          if (p == None) {
            None
          } else {
            p.map(_ -- e.properties.keySet)
          }
        }
        case "$delete" => None
        case _ => p // do nothing for others
      }
    }
  }
}

object EventSeq {
  // Need to
  // >>> import scala.language.implicitConversions
  // to enable implicit conversion. Only import in the code where this is
  // necessary to avoid confusion.
  @deprecated("Use LEvents instead.", "0.9.2")
  implicit def eventSeqToList(es: EventSeq): List[Event] = es.events
  @deprecated("Use LEvents instead.", "0.9.2")
  implicit def listToEventSeq(l: List[Event]): EventSeq = new EventSeq(l)
}


class EventSeq(val events: List[Event]) {
  @deprecated("Use LEvents instead.", "0.9.2")
  def filter(
    eventOpt: Option[String] = None,
    entityTypeOpt: Option[String] = None,
    startTimeOpt: Option[DateTime] = None,
    untilTimeOpt: Option[DateTime] = None): EventSeq = {

    events
    .filter(ViewPredicates.getEventPredicate(eventOpt))
    .filter(ViewPredicates.getStartTimePredicate(startTimeOpt))
    .filter(ViewPredicates.getUntilTimePredicate(untilTimeOpt))
    .filter(ViewPredicates.getEntityTypePredicate(entityTypeOpt))
  }

  @deprecated("Use LEvents instead.", "0.9.2")
  def filter(p: (Event => Boolean)): EventSeq = events.filter(p)

  @deprecated("Use LEvents instead.", "0.9.2")
  def aggregateByEntityOrdered[T](init: T, op: (T, Event) => T)
  : Map[String, T] = {
    events
    .groupBy( _.entityId )
    .mapValues( _.sortBy(_.eventTime.getMillis).foldLeft[T](init)(op))
  }


}


class LBatchView(
  val appId: Int,
  val startTime: Option[DateTime],
  val untilTime: Option[DateTime]) {

  @transient lazy val eventsDb = Storage.getLEvents()

  @transient lazy val _events = eventsDb.find(
    appId = appId,
    startTime = startTime,
    untilTime = untilTime).toList

  @transient lazy val events: EventSeq = new EventSeq(_events)

  /* Aggregate event data
   *
   * @param entityType only aggregate event with entityType
   * @param startTimeOpt if specified, only aggregate event after (inclusive)
   * startTimeOpt
   * @param untilTimeOpt if specified, only aggregate event until (exclusive)
   * endTimeOpt
   */
  @deprecated("Use LEventStore instead.", "0.9.2")
  def aggregateProperties(
      entityType: String,
      startTimeOpt: Option[DateTime] = None,
      untilTimeOpt: Option[DateTime] = None
      ): Map[String, DataMap] = {

    events
    .filter(entityTypeOpt = Some(entityType))
    .filter(e => EventValidation.isSpecialEvents(e.event))
    .aggregateByEntityOrdered(
      init = None,
      op = ViewAggregators.getDataMapAggregator())
    .filter{ case (k, v) => (v != None) }
    .mapValues(_.get)

  }
}
