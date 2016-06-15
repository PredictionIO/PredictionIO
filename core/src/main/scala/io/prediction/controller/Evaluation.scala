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

package io.prediction.controller

import io.prediction.core.BaseEngine
import io.prediction.core.BaseEvaluator
import io.prediction.core.BaseEvaluatorResult

import scala.language.implicitConversions

/** Defines an evaluation that contains an engine and a metric.
  *
  * Implementations of this trait can be supplied to "pio eval" as the first
  * argument.
  *
  * @group Evaluation
  */
trait Evaluation extends Deployment {
  protected [this] var _evaluatorSet: Boolean = false
  protected [this] var _evaluator: BaseEvaluator[_, _, _, _, _ <: BaseEvaluatorResult] = _

  private [prediction]
  def evaluator: BaseEvaluator[_, _, _, _, _ <: BaseEvaluatorResult] = {
    assert(_evaluatorSet, "Evaluator not set")
    _evaluator
  }

  /** Gets the tuple of the [[Engine]] and the implementation of
    * [[io.prediction.core.BaseEvaluator]]
    */
  def engineEvaluator
  : (BaseEngine[_, _, _, _], BaseEvaluator[_, _, _, _, _]) = {
    assert(_evaluatorSet, "Evaluator not set")
    (engine, _evaluator)
  }

  /** Sets both an [[Engine]] and an implementation of
    * [[io.prediction.core.BaseEvaluator]] for this [[Evaluation]]
    *
    * @param engineEvaluator A tuple an [[Engine]] and an implementation of
    *                        [[io.prediction.core.BaseEvaluator]]
    * @tparam EI Evaluation information class
    * @tparam Q Query class
    * @tparam P Predicted result class
    * @tparam A Actual result class
    * @tparam R Metric result class
    */
  def engineEvaluator_=[EI, Q, P, A, R <: BaseEvaluatorResult](
    engineEvaluator: (
      BaseEngine[EI, Q, P, A],
      BaseEvaluator[EI, Q, P, A, R])) {
    assert(!_evaluatorSet, "Evaluator can be set at most once")
    engine = engineEvaluator._1
    _evaluator = engineEvaluator._2
    _evaluatorSet = true
  }

  /** Returns both the [[Engine]] and the implementation of [[Metric]] for this
    * [[Evaluation]]
    */
  def engineMetric: (BaseEngine[_, _, _, _], Metric[_, _, _, _, _]) = {
    throw new NotImplementedError("This method is to keep the compiler happy")
  }

  /** Sets both an [[Engine]] and an implementation of [[Metric]] for this
    * [[Evaluation]]
    *
    * @param engineMetric A tuple of [[Engine]] and an implementation of
    *                     [[Metric]]
    * @tparam EI Evaluation information class
    * @tparam Q Query class
    * @tparam P Predicted result class
    * @tparam A Actual result class
    */
  def engineMetric_=[EI, Q, P, A](
    engineMetric: (BaseEngine[EI, Q, P, A], Metric[EI, Q, P, A, _])) {
    engineEvaluator = (
      engineMetric._1,
      MetricEvaluator(
        metric = engineMetric._2,
        otherMetrics = Seq[Metric[EI, Q, P, A, _]](),
        outputPath = "best.json"))
  }

  private [prediction]
  def engineMetrics: (BaseEngine[_, _, _, _], Metric[_, _, _, _, _]) = {
    throw new NotImplementedError("This method is to keep the compiler happy")
  }

  /** Sets an [[Engine]], an implementation of [[Metric]], and sequence of
    * implementations of [[Metric]] for this [[Evaluation]]
    *
    * @param engineMetrics A tuple of [[Engine]], an implementation of
    *                      [[Metric]] and sequence of implementations of [[Metric]]
    * @tparam EI Evaluation information class
    * @tparam Q Query class
    * @tparam P Predicted result class
    * @tparam A Actual result class
    */
  def engineMetrics_=[EI, Q, P, A](
    engineMetrics: (
      BaseEngine[EI, Q, P, A],
      Metric[EI, Q, P, A, _],
      Seq[Metric[EI, Q, P, A, _]])) {
    engineEvaluator = (
      engineMetrics._1,
      MetricEvaluator(engineMetrics._2, engineMetrics._3))
  }
}
