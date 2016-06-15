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

package io.prediction

/** Provides building blocks for writing a complete prediction engine
  * consisting of DataSource, Preparator, Algorithm, Serving, and Evaluation.
  *
  * == Start Building an Engine ==
  * The starting point of a prediction engine is the [[Engine]] class.
  *
  * == The DASE Paradigm ==
  * The building blocks together form the DASE paradigm. Learn more about DASE
  * [[http://docs.prediction.io/customize/ here]].
  *
  * == Types of Building Blocks ==
  * Depending on the problem you are solving, you would need to pick appropriate
  * flavors of building blocks.
  *
  * === Engines ===
  * There are 3 typical engine configurations:
  *
  *  1. [[PDataSource]], [[PPreparator]], [[P2LAlgorithm]], [[LServing]]
  *  2. [[PDataSource]], [[PPreparator]], [[PAlgorithm]], [[LServing]]
  *  3. [[LDataSource]], [[LPreparator]], [[LAlgorithm]], [[LServing]]
  *
  * In both configurations 1 and 2, data is sourced and prepared in a
  * parallelized fashion, with data type as RDD.
  *
  * The difference between configurations 1 and 2 come at the algorithm stage.
  * In configuration 1, the algorithm operates on potentially large data as RDDs
  * in the Spark cluster, and eventually outputs a model that is small enough to
  * fit in a single machine.
  *
  * On the other hand, configuration 2 outputs a model that is potentially too
  * large to fit in a single machine, and must reside in the Spark cluster as
  * RDD(s).
  *
  * With configuration 1 ([[P2LAlgorithm]]), PredictionIO will automatically
  * try to persist the model to local disk or HDFS if the model is serializable.
  *
  * With configuration 2 ([[PAlgorithm]]), PredictionIO will not automatically
  * try to persist the model, unless the model implements the [[PersistentModel]]
  * trait.
  *
  * In special circumstances where both the data and the model are small,
  * configuration 3 may be used. Beware that RDDs cannot be used with
  * configuration 3.
  *
  * === Data Source ===
  * [[PDataSource]] is probably the most used data source base class with the
  * ability to process RDD-based data. [[LDataSource]] '''cannot''' handle
  * RDD-based data. Use only when you have a special requirement.
  *
  * === Preparator ===
  * With [[PDataSource]], you must pick [[PPreparator]]. The same applies to
  * [[LDataSource]] and [[LPreparator]].
  *
  * === Algorithm ===
  * The workhorse of the engine comes in 3 different flavors.
  *
  * ==== P2LAlgorithm ====
  * Produces a model that is small enough to fit in a single machine from
  * [[PDataSource]] and [[PPreparator]]. The model '''cannot''' contain any RDD.
  * If the produced model is serializable, PredictionIO will try to
  * automatically persist it. In addition, P2LAlgorithm.batchPredict is
  * already implemented for [[Evaluation]] purpose.
  *
  * ==== PAlgorithm ====
  * Produces a model that could contain RDDs from [[PDataSource]] and
  * [[PPreparator]]. PredictionIO will not try to persist it automatically
  * unless the model implements [[PersistentModel]]. [[PAlgorithm.batchPredict]]
  * must be implemented for [[Evaluation]].
  *
  * ==== LAlgorithm ====
  * Produces a model that is small enough to fit in a single machine from
  * [[LDataSource]] and [[LPreparator]]. The model '''cannot''' contain any RDD.
  * If the produced model is serializable, PredictionIO will try to
  * automatically persist it. In addition, LAlgorithm.batchPredict is
  * already implemented for [[Evaluation]] purpose.
  *
  * === Serving ===
  * The serving component comes with only 1 flavor--[[LServing]]. At the serving
  * stage, it is assumed that the result being served is already at a human-
  * consumable size.
  *
  * == Model Persistence ==
  * PredictionIO tries its best to persist trained models automatically. Please
  * refer to [[LAlgorithm.makePersistentModel]],
  * [[P2LAlgorithm.makePersistentModel]], and [[PAlgorithm.makePersistentModel]]
  * for descriptions on different strategies.
  */
package object controller {

  /** Base class of several helper types that represent emptiness
    *
    * @group Helper
    */
  class SerializableClass() extends Serializable

  /** Empty data source parameters.
    * @group Helper
    */
  type EmptyDataSourceParams = EmptyParams

  /** Empty data parameters.
    * @group Helper
    */
  type EmptyDataParams = EmptyParams

  /** Empty evaluation info.
    * @group Helper
    */
  type EmptyEvaluationInfo = SerializableClass

  /** Empty preparator parameters.
    * @group Helper
    */
  type EmptyPreparatorParams = EmptyParams

  /** Empty algorithm parameters.
    * @group Helper
    */
  type EmptyAlgorithmParams = EmptyParams

  /** Empty serving parameters.
    * @group Helper
    */
  type EmptyServingParams = EmptyParams

  /** Empty metrics parameters.
    * @group Helper
    */
  type EmptyMetricsParams = EmptyParams

  /** Empty training data.
    * @group Helper
    */
  type EmptyTrainingData = SerializableClass

  /** Empty prepared data.
    * @group Helper
    */
  type EmptyPreparedData = SerializableClass

  /** Empty model.
    * @group Helper
    */
  type EmptyModel = SerializableClass

  /** Empty actual result.
    * @group Helper
    */
  type EmptyActualResult = SerializableClass

}
