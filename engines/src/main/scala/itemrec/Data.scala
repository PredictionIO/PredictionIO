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

package io.prediction.engines.itemrec

import io.prediction.engines.util

import com.github.nscala_time.time.Imports._

case class Query(
    val uid: String,
    val n: Int // number of items
    ) extends Serializable {
  override def toString = s"[${uid}, ${n}]"
}

// prediction output
case class Prediction(
  // the ranked iid with score
    val items: Seq[(String, Double)]
  ) extends Serializable {
  override def toString = s"${items}"
}

case class Actual(
    // actual items the user has performed actions on
    val iids: Seq[String]
  ) extends Serializable {
  override def toString = s"${iids}"
}

class MetricUnit(
  val score: Double
) extends Serializable

class MetricResult(
  val score: Double
) extends Serializable

class MultipleMetricResult(
  val score: Double
) extends Serializable
