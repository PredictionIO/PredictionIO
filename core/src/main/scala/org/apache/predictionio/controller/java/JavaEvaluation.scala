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

package org.apache.predictionio.controller.java

import org.apache.predictionio.controller.Evaluation
import org.apache.predictionio.controller.Metric
import org.apache.predictionio.core.BaseEngine

import scala.collection.JavaConversions.asScalaBuffer

/** Define an evaluation in Java.
  *
  * Implementations of this abstract class can be supplied to "pio eval" as the first
  * argument.
  *
  * @group Evaluation
  */

abstract class JavaEvaluation extends Evaluation {
  /** Set the [[BaseEngine]] and [[Metric]] for this [[Evaluation]]
    *
    * @param baseEngine [[BaseEngine]] for this [[JavaEvaluation]]
    * @param metric [[Metric]] for this [[JavaEvaluation]]
    * @tparam EI Evaluation information class
    * @tparam Q Query class
    * @tparam P Predicted result class
    * @tparam A Actual result class
    */
  def setEngineMetric[EI, Q, P, A](
    baseEngine: BaseEngine[EI, Q, P, A],
    metric: Metric[EI, Q, P, A, _]) {

    engineMetric = (baseEngine, metric)
  }

  /** Set the [[BaseEngine]] and [[Metric]]s for this [[JavaEvaluation]]
    *
    * @param baseEngine [[BaseEngine]] for this [[JavaEvaluation]]
    * @param metric [[Metric]] for this [[JavaEvaluation]]
    * @param metrics Other [[Metric]]s for this [[JavaEvaluation]]
    * @tparam EI Evaluation information class
    * @tparam Q Query class
    * @tparam P Predicted result class
    * @tparam A Actual result class
    */
  def setEngineMetrics[EI, Q, P, A](
    baseEngine: BaseEngine[EI, Q, P, A],
    metric: Metric[EI, Q, P, A, _],
    metrics: java.util.List[_ <: Metric[EI, Q, P, A, _]]) {

    engineMetrics = (baseEngine, metric, asScalaBuffer(metrics))
  }
}
