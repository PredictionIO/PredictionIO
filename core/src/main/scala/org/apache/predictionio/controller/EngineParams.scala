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

import org.apache.predictionio.core.BaseDataSource
import org.apache.predictionio.core.BaseAlgorithm

import scala.collection.JavaConversions
import scala.language.implicitConversions

/** This class serves as a logical grouping of all required engine's parameters.
  *
  * @param dataSourceParams Data Source name-parameters tuple.
  * @param preparatorParams Preparator name-parameters tuple.
  * @param algorithmParamsList List of algorithm name-parameter pairs.
  * @param servingParams Serving name-parameters tuple.
  * @group Engine
  */
class EngineParams(
    val dataSourceParams: (String, Params) = ("", EmptyParams()),
    val preparatorParams: (String, Params) = ("", EmptyParams()),
    val algorithmParamsList: Seq[(String, Params)] = Seq(),
    val servingParams: (String, Params) = ("", EmptyParams()))
  extends Serializable {

  /** Java-friendly constructor
    *
    * @param dataSourceName Data Source name
    * @param dataSourceParams Data Source parameters
    * @param preparatorName Preparator name
    * @param preparatorParams Preparator parameters
    * @param algorithmParamsList Map of algorithm name-parameters
    * @param servingName Serving name
    * @param servingParams Serving parameters
    */
  def this(
    dataSourceName: String,
    dataSourceParams: Params,
    preparatorName: String,
    preparatorParams: Params,
    algorithmParamsList: _root_.java.util.Map[String, _ <: Params],
    servingName: String,
    servingParams: Params) = {

    // To work around a json4s weird limitation, the parameter names can not be changed
    this(
      (dataSourceName, dataSourceParams),
      (preparatorName, preparatorParams),
      JavaConversions.mapAsScalaMap(algorithmParamsList).toSeq,
      (servingName, servingParams)
    )
  }

  // A case class style copy method.
  def copy(
    dataSourceParams: (String, Params) = dataSourceParams,
    preparatorParams: (String, Params) = preparatorParams,
    algorithmParamsList: Seq[(String, Params)] = algorithmParamsList,
    servingParams: (String, Params) = servingParams): EngineParams = {

    new EngineParams(
      dataSourceParams,
      preparatorParams,
      algorithmParamsList,
      servingParams)
  }
}

/** Companion object for creating [[EngineParams]] instances.
  *
  * @group Engine
  */
object EngineParams {
  /** Create EngineParams.
    *
    * @param dataSourceName Data Source name
    * @param dataSourceParams Data Source parameters
    * @param preparatorName Preparator name
    * @param preparatorParams Preparator parameters
    * @param algorithmParamsList List of algorithm name-parameter pairs.
    * @param servingName Serving name
    * @param servingParams Serving parameters
    */
  def apply(
    dataSourceName: String = "",
    dataSourceParams: Params = EmptyParams(),
    preparatorName: String = "",
    preparatorParams: Params = EmptyParams(),
    algorithmParamsList: Seq[(String, Params)] = Seq(),
    servingName: String = "",
    servingParams: Params = EmptyParams()): EngineParams = {
      new EngineParams(
        dataSourceParams = (dataSourceName, dataSourceParams),
        preparatorParams = (preparatorName, preparatorParams),
        algorithmParamsList = algorithmParamsList,
        servingParams = (servingName, servingParams)
      )
    }
}

/** SimpleEngine has only one algorithm, and uses default preparator and serving
  * layer. Current default preparator is `IdentityPreparator` and serving is
  * `FirstServing`.
  *
  * @tparam TD Training data class.
  * @tparam EI Evaluation info class.
  * @tparam Q Input query class.
  * @tparam P Output prediction class.
  * @tparam A Actual value class.
  * @param dataSourceClass Data source class.
  * @param algorithmClass of algorithm names to classes.
  * @group Engine
  */
class SimpleEngine[TD, EI, Q, P, A](
    dataSourceClass: Class[_ <: BaseDataSource[TD, EI, Q, A]],
    algorithmClass: Class[_ <: BaseAlgorithm[TD, _, Q, P]])
  extends Engine(
    dataSourceClass,
    IdentityPreparator(dataSourceClass),
    Map("" -> algorithmClass),
    LFirstServing(algorithmClass))

/** This shorthand class serves the `SimpleEngine` class.
  *
  * @param dataSourceParams Data source parameters.
  * @param algorithmParams List of algorithm name-parameter pairs.
  * @group Engine
  */
class SimpleEngineParams(
    dataSourceParams: Params = EmptyParams(),
    algorithmParams: Params = EmptyParams())
  extends EngineParams(
    dataSourceParams = ("", dataSourceParams),
    algorithmParamsList = Seq(("", algorithmParams)))


