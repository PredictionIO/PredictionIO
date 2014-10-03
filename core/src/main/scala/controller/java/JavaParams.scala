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

package io.prediction.controller.java

import io.prediction.controller.Params

/** Parameters base class in Java. */
trait JavaParams extends Params

/** Empty parameters. */
class EmptyParams() extends JavaParams {
  override def toString(): String = "Empty"
}

/** Empty data source parameters. */
case class EmptyDataSourceParams() extends EmptyParams

/** Empty data parameters. */
case class EmptyDataParams() extends AnyRef

/** Empty preparator parameters. */
case class EmptyPreparatorParams() extends EmptyParams

/** Empty algorithm parameters. */
case class EmptyAlgorithmParams() extends EmptyParams

/** Empty serving parameters. */
case class EmptyServingParams() extends EmptyParams

/** Empty metrics parameters. */
case class EmptyMetricsParams() extends EmptyParams

/** Empty training data. */
case class EmptyTrainingData() extends AnyRef

/** Empty prepared data. */
case class EmptyPreparedData() extends AnyRef

/** Empty model. */
case class EmptyModel() extends AnyRef

/** Empty input query. */
case class EmptyQuery() extends AnyRef

/** Empty output prediction. */
case class EmptyPrediction() extends AnyRef

/** Empty actual value. */
case class EmptyActual() extends AnyRef
