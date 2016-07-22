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

import akka.actor.Actor
import akka.event.Logging

import com.github.nscala_time.time.Imports.DateTime

/* message to StatsActor */
case class Bookkeeping(val appId: Int, statusCode: StatusCode, event: Event)

/* message to StatsActor */
case class GetStats(val appId: Int)

class StatsActor extends Actor {
  implicit val system = context.system
  val log = Logging(system, this)

  def getCurrent: DateTime = {
    DateTime.now.
      withMinuteOfHour(0).
      withSecondOfMinute(0).
      withMillisOfSecond(0)
  }

  var longLiveStats = new Stats(DateTime.now)
  var hourlyStats = new Stats(getCurrent)

  var prevHourlyStats = new Stats(getCurrent.minusHours(1))
  prevHourlyStats.cutoff(hourlyStats.startTime)

  def bookkeeping(appId: Int, statusCode: StatusCode, event: Event) {
    val current = getCurrent
    // If the current hour is different from the stats start time, we create
    // another stats instance, and move the current to prev.
    if (current != hourlyStats.startTime) {
      prevHourlyStats = hourlyStats
      prevHourlyStats.cutoff(current)
      hourlyStats = new Stats(current)
    }

    hourlyStats.update(appId, statusCode, event)
    longLiveStats.update(appId, statusCode, event)
  }

  def receive: Actor.Receive = {
    case Bookkeeping(appId, statusCode, event) =>
      bookkeeping(appId, statusCode, event)
    case GetStats(appId) => sender() ! Map(
      "time" -> DateTime.now,
      "currentHour" -> hourlyStats.get(appId),
      "prevHour" -> prevHourlyStats.get(appId),
      "longLive" -> longLiveStats.get(appId))
    case _ => log.error("Unknown message.")
  }
}
