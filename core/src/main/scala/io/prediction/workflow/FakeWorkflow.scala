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

package io.prediction.workflow

import io.prediction.annotation.Experimental
// FIXME(yipjustin): Remove wildcard import.
import io.prediction.core._
import io.prediction.controller._

import grizzled.slf4j.Logger
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD


@Experimental
private[prediction] class FakeEngine
extends BaseEngine[EmptyParams, EmptyParams, EmptyParams, EmptyParams] {
  @transient lazy val logger = Logger[this.type]

  def train(
    sc: SparkContext,
    engineParams: EngineParams,
    engineInstanceId: String,
    params: WorkflowParams): Seq[Any] = {
    throw new StopAfterReadInterruption()
  }

  def eval(
    sc: SparkContext,
    engineParams: EngineParams,
    params: WorkflowParams)
  : Seq[(EmptyParams, RDD[(EmptyParams, EmptyParams, EmptyParams)])] = {
    return Seq[(EmptyParams, RDD[(EmptyParams, EmptyParams, EmptyParams)])]()
  }
}

@Experimental
private[prediction] class FakeRunner(f: (SparkContext => Unit))
    extends BaseEvaluator[EmptyParams, EmptyParams, EmptyParams, EmptyParams,
      FakeEvalResult] {
  @transient private lazy val logger = Logger[this.type]
  def evaluateBase(
    sc: SparkContext,
    evaluation: Evaluation,
    engineEvalDataSet:
        Seq[(EngineParams, Seq[(EmptyParams, RDD[(EmptyParams, EmptyParams, EmptyParams)])])],
    params: WorkflowParams): FakeEvalResult = {
    f(sc)
    FakeEvalResult()
  }
}

@Experimental
private[prediction] case class FakeEvalResult() extends BaseEvaluatorResult {
  override val noSave: Boolean = true
}

/** FakeRun allows user to implement custom function under the exact enviroment
  * as other PredictionIO workflow.
  *
  * Useful for developing new features. Only need to extend this trait and
  * implement a function: (SparkContext => Unit). For example, the code below
  * can be run with `pio eval HelloWorld`.
  *
  * {{{
  * object HelloWorld extends FakeRun {
  *   // func defines the function pio runs, must have signature (SparkContext => Unit).
  *   func = f
  *
  *   def f(sc: SparkContext): Unit {
  *     val logger = Logger[this.type]
  *     logger.info("HelloWorld")
  *   }
  * }
  * }}}
  *
  */
@Experimental
trait FakeRun extends Evaluation with EngineParamsGenerator {
  private[this] var _runner: FakeRunner = _

  def runner: FakeRunner = _runner
  def runner_=(r: FakeRunner) {
    engineEvaluator = (new FakeEngine(), r)
    engineParamsList = Seq(new EngineParams())
  }

  def func: (SparkContext => Unit) = { (sc: SparkContext) => Unit }
  def func_=(f: SparkContext => Unit) {
    runner = new FakeRunner(f)
  }
}
