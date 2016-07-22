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

import org.apache.predictionio.annotation.Experimental
import org.apache.predictionio.controller.EngineParams
import org.apache.predictionio.controller.EngineParamsGenerator
import org.apache.predictionio.controller.Evaluation
import org.apache.predictionio.core.BaseEngine
import org.apache.predictionio.core.BaseEvaluator
import org.apache.predictionio.core.BaseEvaluatorResult
import org.apache.predictionio.data.storage.EvaluationInstance

/** Collection of workflow creation methods.
  * @group Workflow
  */
object Workflow {
  // evaluator is already instantiated.
  // This is an undocumented way of using evaluator. Still experimental.
  // evaluatorParams is used to write into EngineInstance, will be shown in
  // dashboard.
  /*
  def runEval[EI, Q, P, A, ER <: AnyRef](
      engine: BaseEngine[EI, Q, P, A],
      engineParams: EngineParams,
      evaluator: BaseEvaluator[EI, Q, P, A, ER],
      evaluatorParams: Params,
      env: Map[String, String] = WorkflowUtils.pioEnvVars,
      params: WorkflowParams = WorkflowParams()) {

    implicit lazy val formats = Utils.json4sDefaultFormats +
      new NameParamsSerializer

    val engineInstance = EngineInstance(
      id = "",
      status = "INIT",
      startTime = DateTime.now,
      endTime = DateTime.now,
      engineId = "",
      engineVersion = "",
      engineVariant = "",
      engineFactory = "FIXME",
      evaluatorClass = evaluator.getClass.getName(),
      batch = params.batch,
      env = env,
      sparkConf = params.sparkEnv,
      dataSourceParams = write(engineParams.dataSourceParams),
      preparatorParams = write(engineParams.preparatorParams),
      algorithmsParams = write(engineParams.algorithmParamsList),
      servingParams = write(engineParams.servingParams),
      evaluatorParams = write(evaluatorParams),
      evaluatorResults = "",
      evaluatorResultsHTML = "",
      evaluatorResultsJSON = "")

    CoreWorkflow.runEval(
      engine = engine,
      engineParams = engineParams,
      engineInstance = engineInstance,
      evaluator = evaluator,
      evaluatorParams = evaluatorParams,
      env = env,
      params = params)
  }
  */

  def runEvaluation(
      evaluation: Evaluation,
      engineParamsGenerator: EngineParamsGenerator,
      env: Map[String, String] = WorkflowUtils.pioEnvVars,
      evaluationInstance: EvaluationInstance = EvaluationInstance(),
      params: WorkflowParams = WorkflowParams()) {
    runEvaluationTypeless(
      evaluation = evaluation,
      engine = evaluation.engine,
      engineParamsList = engineParamsGenerator.engineParamsList,
      evaluationInstance = evaluationInstance,
      evaluator = evaluation.evaluator,
      env = env,
      params = params
    )
  }

  def runEvaluationTypeless[
      EI, Q, P, A, EEI, EQ, EP, EA, ER <: BaseEvaluatorResult](
      evaluation: Evaluation,
      engine: BaseEngine[EI, Q, P, A],
      engineParamsList: Seq[EngineParams],
      evaluationInstance: EvaluationInstance,
      evaluator: BaseEvaluator[EEI, EQ, EP, EA, ER],
      env: Map[String, String] = WorkflowUtils.pioEnvVars,
      params: WorkflowParams = WorkflowParams()) {
    runEvaluationViaCoreWorkflow(
      evaluation = evaluation,
      engine = engine,
      engineParamsList = engineParamsList,
      evaluationInstance = evaluationInstance,
      evaluator = evaluator.asInstanceOf[BaseEvaluator[EI, Q, P, A, ER]],
      env = env,
      params = params)
  }

  /** :: Experimental :: */
  @Experimental
  def runEvaluationViaCoreWorkflow[EI, Q, P, A, R <: BaseEvaluatorResult](
      evaluation: Evaluation,
      engine: BaseEngine[EI, Q, P, A],
      engineParamsList: Seq[EngineParams],
      evaluationInstance: EvaluationInstance,
      evaluator: BaseEvaluator[EI, Q, P, A, R],
      env: Map[String, String] = WorkflowUtils.pioEnvVars,
      params: WorkflowParams = WorkflowParams()) {
    CoreWorkflow.runEvaluation(
      evaluation = evaluation,
      engine = engine,
      engineParamsList = engineParamsList,
      evaluationInstance = evaluationInstance,
      evaluator = evaluator,
      env = env,
      params = params)
  }
}
