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

package io.prediction.engines.itemrank

import io.prediction.engines.util

import com.github.nscala_time.time.Imports._

case class Query(
    val uid: String,
    val iids: Seq[String] // items to be ranked
    ) extends Serializable {
  override def toString = s"[${uid}, ${iids}]"
}

// prediction output
case class Prediction(
  // the ranked iid with score
    val items: Seq[(String, Double)],
    val isOriginal: Boolean = false
  ) extends Serializable {
  override def toString = s"${items}"
}

case class Actual(
    // actual items the user has performed actions on
    val iids: Seq[String],
    // other data that maybe used by metrics.
    val previousActionCount: Int = -1,
    val localDate: LocalDate = new LocalDate(0),
    val localDateTime: LocalDateTime = new LocalDateTime(0),
    val averageOrderSize: Double = -1,
    val previousOrders: Int = -1,
    val variety: Int = -1
  ) extends Serializable {
  override def toString = s"${iids}"
}

class MetricUnit(
  val q: Query,
  val p: Prediction,
  val a: Actual,
  val score: Double,
  val baseline: Double,
  // a hashing function to bucketing uids
  val uidHash: Int = 0
) extends Serializable

class MetricResult(
  val testStartUntil: Tuple2[DateTime, DateTime],
  val baselineMean: Double,
  val baselineStdev: Double,
  val algoMean: Double,
  val algoStdev: Double
) extends Serializable

class MultipleMetricResult(
  val baselineMean: Double,
  val baselineStdev: Double,
  val algoMean: Double,
  val algoStdev: Double
) extends Serializable {
  override def toString = s"baselineMean=${baselineMean} " +
    s"baselineStdDev=${baselineStdev} " +
    s"algoMean=${algoMean} " +
    s"algoStdev=${algoStdev}"
}
