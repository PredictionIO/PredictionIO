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

package org.apache.predictionio.workflow

import org.apache.predictionio.controller.EngineParams
import org.apache.predictionio.controller.Evaluation
import org.apache.predictionio.core.BaseEvaluator
import org.apache.predictionio.core.BaseEvaluatorResult
import org.apache.predictionio.core.BaseEngine

import grizzled.slf4j.Logger
import org.apache.spark.SparkContext

import scala.language.existentials

object EvaluationWorkflow {
  @transient lazy val logger = Logger[this.type]
  def runEvaluation[EI, Q, P, A, R <: BaseEvaluatorResult](
      sc: SparkContext,
      evaluation: Evaluation,
      engine: BaseEngine[EI, Q, P, A],
      engineParamsList: Seq[EngineParams],
      evaluator: BaseEvaluator[EI, Q, P, A, R],
      params: WorkflowParams)
    : R = {
    val engineEvalDataSet = engine.batchEval(sc, engineParamsList, params)
    evaluator.evaluateBase(sc, evaluation, engineEvalDataSet, params)
  }
}
