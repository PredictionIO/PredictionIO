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

import org.apache.predictionio.annotation.DeveloperApi
import org.apache.predictionio.core.BaseAlgorithm
import org.apache.predictionio.workflow.PersistentModelManifest
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

/** Base class of a parallel algorithm.
  *
  * A parallel algorithm can be run in parallel on a cluster and produces a
  * model that can also be distributed across a cluster.
  *
  * If your input query class requires custom JSON4S serialization, the most
  * idiomatic way is to implement a trait that extends [[CustomQuerySerializer]],
  * and mix that into your algorithm class, instead of overriding
  * [[querySerializer]] directly.
  *
  * To provide evaluation feature, one must override and implement the
  * [[batchPredict]] method. Otherwise, an exception will be thrown when pio eval`
  * is used.
  *
  * @tparam PD Prepared data class.
  * @tparam M Trained model class.
  * @tparam Q Input query class.
  * @tparam P Output prediction class.
  * @group Algorithm
  */
abstract class PAlgorithm[PD, M, Q, P]
  extends BaseAlgorithm[PD, M, Q, P] {

  def trainBase(sc: SparkContext, pd: PD): M = train(sc, pd)

  /** Implement this method to produce a model from prepared data.
    *
    * @param pd Prepared data for model training.
    * @return Trained model.
    */
  def train(sc: SparkContext, pd: PD): M

  def batchPredictBase(sc: SparkContext, bm: Any, qs: RDD[(Long, Q)])
  : RDD[(Long, P)] = batchPredict(bm.asInstanceOf[M], qs)

  /** To provide evaluation feature, one must override and implement this method
    * to generate many predictions in batch. Otherwise, an exception will be
    * thrown when `pio eval` is used.
    *
    * The default implementation throws an exception.
    *
    * @param m Trained model produced by [[train]].
    * @param qs An RDD of index-query tuples. The index is used to keep track of
    *           predicted results with corresponding queries.
    */
  def batchPredict(m: M, qs: RDD[(Long, Q)]): RDD[(Long, P)] =
    throw new NotImplementedError("batchPredict not implemented")

  def predictBase(baseModel: Any, query: Q): P = {
    predict(baseModel.asInstanceOf[M], query)
  }

  /** Implement this method to produce a prediction from a query and trained
    * model.
    *
    * @param model Trained model produced by [[train]].
    * @param query An input query.
    * @return A prediction.
    */
  def predict(model: M, query: Q): P

  /** :: DeveloperApi ::
    * Engine developers should not use this directly (read on to see how parallel
    * algorithm models are persisted).
    *
    * In general, parallel models may contain multiple RDDs. It is not easy to
    * infer and persist them programmatically since these RDDs may be
    * potentially huge. To persist these models, engine developers need to  mix
    * the [[PersistentModel]] trait into the model class and implement
    * [[PersistentModel.save]]. If it returns true, a
    * [[org.apache.predictionio.workflow.PersistentModelManifest]] will be
    * returned so that during deployment, PredictionIO will use
    * [[PersistentModelLoader]] to retrieve the model. Otherwise, Unit will be
    * returned and the model will be re-trained on-the-fly.
    *
    * @param sc Spark context
    * @param modelId Model ID
    * @param algoParams Algorithm parameters that trained this model
    * @param bm Model
    * @return The model itself for automatic persistence, an instance of
    *         [[org.apache.predictionio.workflow.PersistentModelManifest]] for manual
    *         persistence, or Unit for re-training on deployment
    */
  @DeveloperApi
  override
  def makePersistentModel(
    sc: SparkContext,
    modelId: String,
    algoParams: Params,
    bm: Any): Any = {
    val m = bm.asInstanceOf[M]
    if (m.isInstanceOf[PersistentModel[_]]) {
      if (m.asInstanceOf[PersistentModel[Params]].save(
        modelId, algoParams, sc)) {
        PersistentModelManifest(className = m.getClass.getName)
      } else {
        Unit
      }
    } else {
      Unit
    }
  }
}
