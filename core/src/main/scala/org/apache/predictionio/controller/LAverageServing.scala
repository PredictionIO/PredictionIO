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

package org.apache.predictionio.controller

import org.apache.predictionio.core.BaseAlgorithm

/** A concrete implementation of [[LServing]] returning the average of all
  * algorithms' predictions, where their classes are expected to be all Double.
  *
  * @group Serving
  */
class LAverageServing[Q] extends LServing[Q, Double] {
  /** Returns the average of all algorithms' predictions. */
  def serve(query: Q, predictions: Seq[Double]): Double = {
    predictions.sum / predictions.length
  }
}

/** A concrete implementation of [[LServing]] returning the average of all
  * algorithms' predictions, where their classes are expected to be all Double.
  *
  * @group Serving
  */
object LAverageServing {
  /** Returns an instance of [[LAverageServing]]. */
  def apply[Q](a: Class[_ <: BaseAlgorithm[_, _, Q, _]]): Class[LAverageServing[Q]] =
    classOf[LAverageServing[Q]]
}
