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

package io.prediction

import io.prediction.controller.EmptyParams

/** Provides building blocks for writing a complete prediction engine
  * consisting of DataSource, Preparator, Algorithm, Serving, and Metrics.
  */
package object controller {

  /** Empty data source parameters.
    * @group General
    */
  type EmptyDataSourceParams = EmptyParams

  /** Empty data parameters.
    * @group General
    */
  type EmptyDataParams = AnyRef

  /** Empty preparator parameters.
    * @group General
    */
  type EmptyPreparatorParams = EmptyParams

  /** Empty algorithm parameters.
    * @group General
    */
  type EmptyAlgorithmParams = EmptyParams

  /** Empty serving parameters.
    * @group General
    */
  type EmptyServingParams = EmptyParams

  /** Empty metrics parameters.
    * @group General
    */
  type EmptyMetricsParams = EmptyParams

  /** Empty training data.
    * @group General
    */
  type EmptyTrainingData = AnyRef

  /** Empty prepared data.
    * @group General
    */
  type EmptyPreparedData = AnyRef

  /** Empty model.
    * @group General
    */
  type EmptyModel = AnyRef

  /** Empty input query.
    * @group General
    */
  type EmptyQuery = AnyRef

  /** Empty output prediction.
    * @group General
    */
  type EmptyPrediction = AnyRef

  /** Empty actual value.
    * @group General
    */
  type EmptyActual = AnyRef

}
