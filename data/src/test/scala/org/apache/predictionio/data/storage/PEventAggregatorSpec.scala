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

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD


class PEventAggregatorSpec extends Specification with TestEvents {

  System.clearProperty("spark.driver.port")
  System.clearProperty("spark.hostPort")
  val sc = new SparkContext("local[4]", "PEventAggregatorSpec test")

  "PEventAggregator" should {

    "aggregate two entities' properties as DataMap/PropertyMap correctly" in {
      val events = sc.parallelize(Seq(
        u1e5, u2e2, u1e3, u1e1, u2e3, u2e1, u1e4, u1e2))

      val users = PEventAggregator.aggregateProperties(events)

      val userMap = users.collectAsMap.toMap
      val expectedDM = Map(
        "u1" -> DataMap(u1),
        "u2" -> DataMap(u2)
      )

      val expectedPM = Map(
        "u1" -> PropertyMap(u1, u1BaseTime, u1LastTime),
        "u2" -> PropertyMap(u2, u2BaseTime, u2LastTime)
      )

      userMap must beEqualTo(expectedDM)
      userMap must beEqualTo(expectedPM)
    }

    "aggregate deleted entity correctly" in {
      // put the delete event in middle
      val events = sc.parallelize(Seq(
        u1e5, u2e2, u1e3, u1ed, u1e1, u2e3, u2e1, u1e4, u1e2))

      val users = PEventAggregator.aggregateProperties(events)

      val userMap = users.collectAsMap.toMap
      val expectedPM = Map(
        "u2" -> PropertyMap(u2, u2BaseTime, u2LastTime)
      )

      userMap must beEqualTo(expectedPM)
    }

  }

  step(sc.stop())
}
