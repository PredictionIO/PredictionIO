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

package org.apache.predictionio.data.view

import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.LEvents
import org.apache.predictionio.data.storage.EventValidation
import org.apache.predictionio.data.storage.DataMap
import org.apache.predictionio.data.storage.Storage

import scala.concurrent.ExecutionContext.Implicits.global // TODO

import grizzled.slf4j.Logger
import org.joda.time.DateTime

import scala.language.implicitConversions

class TestHBLEvents() {
  @transient lazy val eventsDb = Storage.getLEvents()

  def run(): Unit = {
    val r = eventsDb.find(
      appId = 1,
      startTime = None,
      untilTime = None,
      entityType = Some("pio_user"),
      entityId = Some("3")).toList
    println(r)
  }
}

class TestSource(val appId: Int) {
  @transient lazy val logger = Logger[this.type]
  @transient lazy val batchView = new LBatchView(appId,
    None, None)

  def run(): Unit = {
    println(batchView.events)
  }
}

object QuickTest {

  def main(args: Array[String]) {
    val t = new TestHBLEvents()
    t.run()

    // val ts = new TestSource(args(0).toInt)
    // ts.run()
  }
}

object TestEventTime {
  @transient lazy val batchView = new LBatchView(9, None, None)

  // implicit def back2list(es: EventSeq) = es.events

  def main(args: Array[String]) {
    val e = batchView.events.filter(
      eventOpt = Some("rate"),
      startTimeOpt = Some(new DateTime(1998, 1, 1, 0, 0))
      // untilTimeOpt = Some(new DateTime(1997, 1, 1, 0, 0))
    )
      // untilTimeOpt = Some(new DateTime(2000, 1, 1, 0, 0)))

    e.foreach { println }
    println()
    println()
    println()
    val u = batchView.aggregateProperties("pio_item")
    u.foreach { println }
    println()
    println()
    println()

    // val l: Seq[Event] = e
    val l = e.map { _.entityId }
    l.foreach { println }
  }

}
