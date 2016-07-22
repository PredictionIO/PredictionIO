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

import org.specs2.mutable._

import org.json4s.JObject
import org.json4s.native.JsonMethods.parse

import org.joda.time.DateTime

class LEventAggregatorSpec extends Specification with TestEvents {

  "LEventAggregator.aggregateProperties()" should {

    "aggregate two entities' properties as DataMap correctly" in {
      val events = Vector(u1e5, u2e2, u1e3, u1e1, u2e3, u2e1, u1e4, u1e2)
      val result: Map[String, DataMap] =
        LEventAggregator.aggregateProperties(events.toIterator)

      val expected = Map(
        "u1" -> DataMap(u1),
        "u2" -> DataMap(u2)
      )

      result must beEqualTo(expected)
    }

    "aggregate two entities' properties as PropertyMap correctly" in {
      val events = Vector(u1e5, u2e2, u1e3, u1e1, u2e3, u2e1, u1e4, u1e2)
      val result: Map[String, PropertyMap] =
        LEventAggregator.aggregateProperties(events.toIterator)

      val expected = Map(
        "u1" -> PropertyMap(u1, u1BaseTime, u1LastTime),
        "u2" -> PropertyMap(u2, u2BaseTime, u2LastTime)
      )

      result must beEqualTo(expected)
    }


    "aggregate deleted entity correctly" in {
      val events = Vector(u1e5, u2e2, u1e3, u1ed, u1e1, u2e3, u2e1, u1e4, u1e2)

      val result = LEventAggregator.aggregateProperties(events.toIterator)
      val expected = Map(
        "u2" -> PropertyMap(u2, u2BaseTime, u2LastTime)
      )

      result must beEqualTo(expected)
    }
  }


  "LEventAggregator.aggregatePropertiesSingle()" should {

    "aggregate single entity properties as DataMap correctly" in {
        val events = Vector(u1e5, u1e3, u1e1, u1e4, u1e2)
        val eventsIt = events.toIterator

        val result: Option[DataMap] = LEventAggregator
          .aggregatePropertiesSingle(eventsIt)
        val expected = DataMap(u1)

        result must beEqualTo(Some(expected))
    }

    "aggregate single entity properties as PropertyMap correctly" in {
        val events = Vector(u1e5, u1e3, u1e1, u1e4, u1e2)
        val eventsIt = events.toIterator

        val result: Option[PropertyMap] = LEventAggregator
          .aggregatePropertiesSingle(eventsIt)
        val expected = PropertyMap(u1, u1BaseTime, u1LastTime)

        result must beEqualTo(Some(expected))
    }

    "aggregate deleted entity correctly" in {
      // put the delete event in the middle
      val events = Vector(u1e4, u1e2, u1ed, u1e3, u1e1, u1e5)
      val eventsIt = events.toIterator

      val result = LEventAggregator.aggregatePropertiesSingle(eventsIt)

      result must beEqualTo(None)
    }
  }
}
