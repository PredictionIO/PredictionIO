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

package org.apache.predictionio.data.api

import org.apache.predictionio.data.storage.Event

import spray.http.StatusCode

import scala.collection.mutable.{ HashMap => MHashMap }
import scala.collection.mutable

import com.github.nscala_time.time.Imports.DateTime

case class EntityTypesEvent(
  val entityType: String,
  val targetEntityType: Option[String],
  val event: String) {

  def this(e: Event) = this(
    e.entityType,
    e.targetEntityType,
    e.event)
}

case class KV[K, V](key: K, value: V)

case class StatsSnapshot(
  val startTime: DateTime,
  val endTime: Option[DateTime],
  val basic: Seq[KV[EntityTypesEvent, Long]],
  val statusCode: Seq[KV[StatusCode, Long]]
)


class Stats(val startTime: DateTime) {
  private[this] var _endTime: Option[DateTime] = None
  var statusCodeCount = MHashMap[(Int, StatusCode), Long]().withDefaultValue(0L)
  var eteCount = MHashMap[(Int, EntityTypesEvent), Long]().withDefaultValue(0L)

  def cutoff(endTime: DateTime) {
    _endTime = Some(endTime)
  }

  def update(appId: Int, statusCode: StatusCode, event: Event) {
    statusCodeCount((appId, statusCode)) += 1
    eteCount((appId, new EntityTypesEvent(event))) += 1
  }

  def extractByAppId[K, V](appId: Int, m: mutable.Map[(Int, K), V])
  : Seq[KV[K, V]] = {
    m
    .toSeq
    .flatMap { case (k, v) =>
      if (k._1 == appId) { Seq(KV(k._2, v)) } else { Seq() }
    }
  }

  def get(appId: Int): StatsSnapshot = {
    StatsSnapshot(
      startTime,
      _endTime,
      extractByAppId(appId, eteCount),
      extractByAppId(appId, statusCodeCount)
    )
  }
}
