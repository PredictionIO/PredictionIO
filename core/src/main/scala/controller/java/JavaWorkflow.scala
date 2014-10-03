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

package io.prediction.controller.java;

import io.prediction.workflow.JavaCoreWorkflow;
import io.prediction.controller.Engine
import io.prediction.controller.EngineParams
import io.prediction.controller.WorkflowParams
import io.prediction.core.BaseMetrics
import io.prediction.controller.Params
import scala.collection.JavaConversions._

/** Collection of workflow creation methods. */
object JavaWorkflow {
  /** Creates a workflow that runs an engine.
    *
    * @tparam DP Data preparator class.
    * @tparam TD Training data class.
    * @tparam PD Prepared data class.
    * @tparam Q Input query class.
    * @tparam P Output prediction class.
    * @tparam A Actual value class.
    *
    * @param engine An instance of [[Engine]].
    * @param engineParams Engine parameters.
    * @param params Workflow parameters.
    */
  def runEngine[DP, TD, PD, Q, P, A](
    engine: Engine[TD, DP, PD, Q, P, A],
    engineParams: EngineParams,
    params: WorkflowParams
  ) {
    runEngine(
      engine = engine,
      engineParams = engineParams,
      metricsClass = null,
      metricsParams = null,
      params = params
    )
  }

  /** Creates a workflow that runs an engine.
    *
    * @tparam DP Data preparator class.
    * @tparam TD Training data class.
    * @tparam PD Prepared data class.
    * @tparam Q Input query class.
    * @tparam P Output prediction class.
    * @tparam A Actual value class.
    *
    * @param engine An instance of [[Engine]].
    * @param engineParams Engine parameters.
    * @param metricsClass Metrics class.
    * @param metricsParams Metrics parameters.
    * @param params Workflow parameters.
    */
  def runEngine[DP, TD, PD, Q, P, A, MU, MR, MMR <: AnyRef](
      engine: Engine[TD, DP, PD, Q, P, A],
      engineParams: EngineParams,
      metricsClass
        : Class[_ <: BaseMetrics[_ <: Params, DP, Q, P, A, MU, MR, MMR]],
      metricsParams: Params,
      params: WorkflowParams
    ) {
    JavaCoreWorkflow.run(
      dataSourceClass = engine.dataSourceClass,
      dataSourceParams = engineParams.dataSourceParams,
      preparatorClass = engine.preparatorClass,
      preparatorParams = engineParams.preparatorParams,
      algorithmClassMap = engine.algorithmClassMap,
      algorithmParamsList = engineParams.algorithmParamsList,
      servingClass = engine.servingClass,
      servingParams = engineParams.servingParams,
      metricsClass = metricsClass,
      metricsParams = metricsParams,
      params = params
    )
  }
}
