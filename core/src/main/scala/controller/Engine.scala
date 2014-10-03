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

import io.prediction.core.BaseDataSource
import io.prediction.core.BasePreparator
import io.prediction.core.BaseAlgorithm
import io.prediction.core.BaseServing

/** This class chains up the entire data process. PredictionIO uses this
  * information to create workflows and deployments. In Scala, you should
  * implement an object that extends the `IEngineFactory` trait similar to the
  * following example.
  *
  * {{{
  * object ItemRankEngine extends IEngineFactory {
  *   def apply() = {
  *     new Engine(
  *       classOf[ItemRankDataSource],
  *       classOf[ItemRankPreparator],
  *       Map(
  *         "knn" -> classOf[KNNAlgorithm],
  *         "rand" -> classOf[RandomAlgorithm],
  *         "mahoutItemBased" -> classOf[MahoutItemBasedAlgorithm]),
  *       classOf[ItemRankServing])
  *   }
  * }
  * }}}
  *
  * @see [[IEngineFactory]]
  * @tparam TD Training data class.
  * @tparam DP Data parameters class.
  * @tparam PD Prepared data class.
  * @tparam Q Input query class.
  * @tparam P Output prediction class.
  * @tparam A Actual value class.
  * @param dataSourceClass Data source class.
  * @param preparatorClass Preparator class.
  * @param algorithmClassMap Map of algorithm names to classes.
  * @param servingClass Serving class.
  * @group Engine
  */
class Engine[TD, DP, PD, Q, P, A](
    val dataSourceClass: Class[_ <: BaseDataSource[_ <: Params, DP, TD, Q, A]],
    val preparatorClass: Class[_ <: BasePreparator[_ <: Params, TD, PD]],
    val algorithmClassMap:
      Map[String, Class[_ <: BaseAlgorithm[_ <: Params, PD, _, Q, P]]],
    val servingClass: Class[_ <: BaseServing[_ <: Params, Q, P]])
  extends Serializable

/** This class serves as a logical grouping of all required engine's parameters.
  *
  * @param dataSourceParams Data source parameters.
  * @param preparatorParams Preparator parameters.
  * @param algorithmParamsList List of algorithm name-parameter pairs.
  * @param servingParams Serving parameters.
  * @group Engine
  */
class EngineParams(
    val dataSourceParams: Params = EmptyParams(),
    val preparatorParams: Params = EmptyParams(),
    val algorithmParamsList: Seq[(String, Params)] = Seq(),
    val servingParams: Params = EmptyParams())
  extends Serializable

/** SimpleEngine has only one algorithm, and uses default preparator and serving
  * layer. Current default preparator is `IdentityPreparator` and serving is
  * `FirstServing`.
  *
  * @tparam TD Training data class.
  * @tparam DP Data parameters class.
  * @tparam PD Prepared data class.
  * @tparam Q Input query class.
  * @tparam P Output prediction class.
  * @tparam A Actual value class.
  * @param dataSourceClass Data source class.
  * @param algorithmClassMap Map of algorithm names to classes.
  * @group Engine
  */
class SimpleEngine[TD, DP, Q, P, A](
    dataSourceClass: Class[_ <: BaseDataSource[_ <: Params, DP, TD, Q, A]],
    algorithmClass: Class[_ <: BaseAlgorithm[_ <: Params, TD, _, Q, P]])
  extends Engine(
    dataSourceClass,
    IdentityPreparator(dataSourceClass),
    Map("" -> algorithmClass),
    FirstServing(algorithmClass))

/** This shorthand class serves the `SimpleEngine` class.
  *
  * @param dataSourceParams Data source parameters.
  * @param algorithmParamsList List of algorithm name-parameter pairs.
  * @group Engine
  */
class SimpleEngineParams(
    dataSourceParams: Params = EmptyParams(),
    algorithmParams: Params = EmptyParams())
  extends EngineParams(
    dataSourceParams = dataSourceParams,
    algorithmParamsList = Seq(("", algorithmParams)))

/** If you intend to let PredictionIO create workflow and deploy serving
  * automatically, you will need to implement an object that extends this trait
  * and return an [[Engine]].
  *
  * @group Engine
  */
trait IEngineFactory {
  /** Creates an instance of an [[Engine]]. */
  def apply(): Engine[_, _, _, _, _, _]
}
