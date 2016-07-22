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
import org.joda.time.DateTime

/** :: DeveloperApi ::
  * Provides aggregation support of [[Event]]s to [[LEvents]]. Engine developers
  * should use [[org.apache.predictionio.data.store.LEventStore]] instead of using this
  * directly.
  *
  * @group Event Data
  */
@DeveloperApi
object LEventAggregator {
  /** :: DeveloperApi ::
    * Aggregate all properties grouped by entity type given an iterator of
    * [[Event]]s with the latest property values from all [[Event]]s, and their
    * first and last updated time
    *
    * @param events An iterator of [[Event]]s whose properties will be aggregated
    * @return A map of entity type to [[PropertyMap]]
    */
  @DeveloperApi
  def aggregateProperties(events: Iterator[Event]): Map[String, PropertyMap] = {
    events.toList
      .groupBy(_.entityId)
      .mapValues(_.sortBy(_.eventTime.getMillis)
        .foldLeft[Prop](Prop())(propAggregator))
      .filter{ case (k, v) => v.dm.isDefined }
      .mapValues{ v =>
        require(v.firstUpdated.isDefined,
          "Unexpected Error: firstUpdated cannot be None.")
        require(v.lastUpdated.isDefined,
          "Unexpected Error: lastUpdated cannot be None.")

        PropertyMap(
          fields = v.dm.get.fields,
          firstUpdated = v.firstUpdated.get,
          lastUpdated = v.lastUpdated.get
        )
      }
  }

  /** :: DeveloperApi ::
    * Aggregate all properties given an iterator of [[Event]]s with the latest
    * property values from all [[Event]]s, and their first and last updated time
    *
    * @param events An iterator of [[Event]]s whose properties will be aggregated
    * @return An optional [[PropertyMap]]
    */
  @DeveloperApi
  def aggregatePropertiesSingle(events: Iterator[Event])
  : Option[PropertyMap] = {
    val prop = events.toList
      .sortBy(_.eventTime.getMillis)
      .foldLeft[Prop](Prop())(propAggregator)

    prop.dm.map{ d =>
      require(prop.firstUpdated.isDefined,
        "Unexpected Error: firstUpdated cannot be None.")
      require(prop.lastUpdated.isDefined,
        "Unexpected Error: lastUpdated cannot be None.")

      PropertyMap(
        fields = d.fields,
        firstUpdated = prop.firstUpdated.get,
        lastUpdated = prop.lastUpdated.get
      )
    }
  }

  /** Event names that control aggregation: \$set, \$unset, and \$delete */
  val eventNames = List("$set", "$unset", "$delete")

  private
  def dataMapAggregator: ((Option[DataMap], Event) => Option[DataMap]) = {
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

  private
  def propAggregator: ((Prop, Event) => Prop) = {
    (p, e) => {
      e.event match {
        case "$set" | "$unset" | "$delete" => {
          Prop(
            dm = dataMapAggregator(p.dm, e),
            firstUpdated = p.firstUpdated.map { t =>
              first(t, e.eventTime)
            }.orElse(Some(e.eventTime)),
            lastUpdated = p.lastUpdated.map { t =>
              last(t, e.eventTime)
            }.orElse(Some(e.eventTime))
          )
        }
        case _ => p // do nothing for others
      }
    }
  }

  private
  def first(a: DateTime, b: DateTime): DateTime = if (b.isBefore(a)) b else a

  private
  def last(a: DateTime, b: DateTime): DateTime = if (b.isAfter(a)) b else a

  private case class Prop(
    dm: Option[DataMap] = None,
    firstUpdated: Option[DateTime] = None,
    lastUpdated: Option[DateTime] = None
  )
}
