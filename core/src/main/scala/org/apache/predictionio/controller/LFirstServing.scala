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

/** A concrete implementation of [[LServing]] returning the first algorithm's
  * prediction result directly without any modification.
  *
  * @group Serving
  */
class LFirstServing[Q, P] extends LServing[Q, P] {
  /** Returns the first algorithm's prediction. */
  def serve(query: Q, predictions: Seq[P]): P = predictions.head
}

/** A concrete implementation of [[LServing]] returning the first algorithm's
  * prediction result directly without any modification.
  *
  * @group Serving
  */
object LFirstServing {
  /** Returns an instance of [[LFirstServing]]. */
  def apply[Q, P](a: Class[_ <: BaseAlgorithm[_, _, Q, P]]): Class[LFirstServing[Q, P]] =
    classOf[LFirstServing[Q, P]]
}
