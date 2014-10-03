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

import scala.concurrent.ExecutionContext.Implicits.global // TODO

import grizzled.slf4j.Logger

class TestHBEvents() {
  @transient lazy val eventsDb = Storage.getEventDataEvents()

  def run() = {
    val r = eventsDb
      .getByAppIdAndTimeAndEntity(
        1, None, None,
        Some("pio_user"), Some("3")).right.get.toList
    println(r)
  }
}

class TestSource(val appId: Int) {
  @transient lazy val logger = Logger[this.type]
  @transient lazy val batchView = new LBatchView(appId,
    None, None)

  def run() = {
    println(batchView.events)
  }
}

object QuickTest {

  def main(args: Array[String]) {
    val t = new TestHBEvents()
    t.run()

    //val ts = new TestSource(args(0).toInt)
    //ts.run()
  }
}
