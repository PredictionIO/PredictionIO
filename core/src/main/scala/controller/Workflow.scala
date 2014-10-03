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

package io.prediction.controller

import io.prediction.core.BaseAlgorithm
import io.prediction.core.BaseDataSource
import io.prediction.core.BaseMetrics
import io.prediction.core.BasePreparator
import io.prediction.core.BaseServing
import io.prediction.core.Doer
import io.prediction.core.LModelAlgorithm
import io.prediction.workflow.WorkflowUtils
import io.prediction.workflow.CoreWorkflow
import scala.reflect.ClassTag

/** Workflow parameters.
  *
  * @param batch Batch label of the run.
  * @param verbose Verbosity level.
  * @param saveModel Controls whether trained models are persisted.
  * @group Workflow
  */
case class WorkflowParams(
  batch: String = "",
  verbose: Int = 2,
  saveModel: Boolean = true)

/** Collection of workflow creation methods.
  * @group Workflow
  */
object Workflow {
  /** Creates a workflow that runs an engine.
    *
    * @tparam DP Data preparator class.
    * @tparam TD Training data class.
    * @tparam PD Prepared data class.
    * @tparam Q Input query class.
    * @tparam P Output prediction class.
    * @tparam A Actual value class.
    * @tparam MU Metrics unit class.
    * @tparam MR Metrics result class.
    * @tparam MMR Multiple metrics results class.
    * @param params Workflow parameters.
    * @param engine An instance of [[Engine]].
    * @param engineParams Engine parameters.
    * @param metricsClassOpt Optional metrics class.
    * @param metricsParams Metrics parameters.
    */
  def runEngine[
      DP, TD, PD, Q, P, A,
      MU : ClassTag, MR : ClassTag, MMR <: AnyRef :ClassTag
      ](
      params: WorkflowParams = WorkflowParams(),
      engine: Engine[TD, DP, PD, Q, P, A],
      engineParams: EngineParams,
      metricsClassOpt
        : Option[Class[_ <: BaseMetrics[_ <: Params, DP, Q, P, A, MU, MR, MMR]]]
        = None,
      metricsParams: Params = EmptyParams()) {

    run(
      dataSourceClassOpt = Some(engine.dataSourceClass),
      dataSourceParams = engineParams.dataSourceParams,
      preparatorClassOpt = Some(engine.preparatorClass),
      preparatorParams = engineParams.preparatorParams,
      algorithmClassMapOpt = Some(engine.algorithmClassMap),
      algorithmParamsList = engineParams.algorithmParamsList,
      servingClassOpt = Some(engine.servingClass),
      servingParams = engineParams.servingParams,
      metricsClassOpt = metricsClassOpt,
      metricsParams = metricsParams,
      params = params
    )
  }

  /** Creates a workflow that runs a collection of engine components.
    *
    * @tparam DP Data preparator class.
    * @tparam TD Training data class.
    * @tparam PD Prepared data class.
    * @tparam Q Input query class.
    * @tparam P Output prediction class.
    * @tparam A Actual value class.
    * @tparam MU Metrics unit class.
    * @tparam MR Metrics result class.
    * @tparam MMR Multiple metrics results class.
    * @param dataSourceClassOpt Optional data source class.
    * @param dataSourceParams Data source parameters.
    * @param preparatorClassOpt Optional preparator class.
    * @param preparatorParams Preparator parameters.
    * @param algorithmClassMapOpt Optional map of algorithm names to classes.
    * @param algorithmParamsList List of instantiated algorithms and their
    *                            parameters.
    * @param servingClassOpt Optional serving class.
    * @param servingParams Serving parameters.
    * @param metricsClassOpt Optional metrics class.
    * @param metricsParams Metrics parameters.
    * @param params Workflow parameters.
    */
  def run[
      DP, TD, PD, Q, P, A,
      MU : ClassTag, MR : ClassTag, MMR <: AnyRef :ClassTag
      ](
      dataSourceClassOpt
        : Option[Class[_ <: BaseDataSource[_ <: Params, DP, TD, Q, A]]] = None,
      dataSourceParams: Params = EmptyParams(),
      preparatorClassOpt
        : Option[Class[_ <: BasePreparator[_ <: Params, TD, PD]]] = None,
      preparatorParams: Params = EmptyParams(),
      algorithmClassMapOpt
        : Option[Map[String, Class[_ <: BaseAlgorithm[_ <: Params, PD, _, Q, P]]]]
        = None,
      algorithmParamsList: Seq[(String, Params)] = null,
      servingClassOpt: Option[Class[_ <: BaseServing[_ <: Params, Q, P]]]
        = None,
      servingParams: Params = EmptyParams(),
      metricsClassOpt
        : Option[Class[_ <: BaseMetrics[_ <: Params, DP, Q, P, A, MU, MR, MMR]]]
        = None,
      metricsParams: Params = EmptyParams(),
      params: WorkflowParams = WorkflowParams()
    ) {

    CoreWorkflow.runTypeless(
        dataSourceClassOpt, dataSourceParams,
        preparatorClassOpt, preparatorParams,
        algorithmClassMapOpt, algorithmParamsList,
        servingClassOpt, servingParams,
        metricsClassOpt, metricsParams,
        params = params
      )
  }

}
