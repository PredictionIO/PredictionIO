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

package io.prediction.data.storage.examples

import io.prediction.data.storage.hbase.HBPEvents
import io.prediction.data.storage.Event

import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD

object HBPEventsTest {

  def main(args: Array[String]) {

    val appId = args(0).toInt
    val sparkConf = new SparkConf().setAppName("HBaseTest")
    val sc = new SparkContext(sparkConf)
    val eventClient = new HBPEvents("predictionio_eventdata")
    val e: RDD[Event] = eventClient.getByAppIdAndTimeAndEntity(appId,
      None, None, None, None)(sc)

    println(e.collect().mkString(","))

  }
}
