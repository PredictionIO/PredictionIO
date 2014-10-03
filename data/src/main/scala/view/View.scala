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

package io.prediction.data.view

import io.prediction.data.storage.Event
import io.prediction.data.storage.Events
import io.prediction.data.storage.EventValidation
import io.prediction.data.storage.DataMap
import io.prediction.data.storage.Storage

import org.joda.time.DateTime

import scala.concurrent.ExecutionContext.Implicits.global // TODO

class LBatchView(
  val appId: Int,
  val startTime: Option[DateTime],
  val untilTime: Option[DateTime]) {

  @transient lazy val eventsDb = Storage.getEventDataEvents()

  @transient lazy val events = eventsDb.getByAppIdAndTime(appId,
    startTime, untilTime).right.get.toList

  def aggregateProperties(entityType: String): Map[String, DataMap] = {

    def predicate(e: Event) = (e.entityType == entityType) &&
      (EventValidation.isSpecialEvents(e.event))
      //((e.event == "$set") || (e.event == "$unset"))

    def aggregate(p: Option[DataMap], e: Event): Option[DataMap] = {
      e.event match {
        case "$set" => {
          if (p == None)
            Some(e.properties)
          else
            p.map(_ ++ e.properties)
        }
        case "$unset" => {
          if (p == None)
            None
          else
            p.map(_ -- e.properties.keySet)
        }
        case "$delete" => None
        case _ => p // do nothing for others
      }
    }

    aggregateByEntityOrdered[Option[DataMap]](
      predicate, None, aggregate)
      .filter{ case (k, v) => (v != None) }
      .mapValues(_.get)
  }

  def aggregateByEntityOrdered[T](
    //events: Seq[Event],
    predicate: Event => Boolean,
    init: T,
    op: (T, Event) => T): Map[String, T] = {

    events.filter( predicate(_) )
      .groupBy( _.entityId )
      .mapValues( _.sortBy(_.eventTime.getMillis).foldLeft[T](init)(op))
      .toMap

  }

  def groupByEntityOrdered[T](
    //events: Seq[Event],
    predicate: Event => Boolean,
    map: Event => T): Map[String, Seq[T]] = {

    events.filter( predicate(_) )
      .groupBy( _.entityId )
      .mapValues( _.sortBy(_.eventTime.getMillis).map(map(_)) )
      .toMap
  }

}
