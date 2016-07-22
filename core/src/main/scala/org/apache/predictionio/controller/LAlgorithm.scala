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

import _root_.org.apache.predictionio.annotation.DeveloperApi
import org.apache.predictionio.core.BaseAlgorithm
import org.apache.predictionio.workflow.PersistentModelManifest
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import scala.reflect._

/** Base class of a local algorithm.
  *
  * A local algorithm runs locally within a single machine and produces a model
  * that can fit within a single machine.
  *
  * If your input query class requires custom JSON4S serialization, the most
  * idiomatic way is to implement a trait that extends [[CustomQuerySerializer]],
  * and mix that into your algorithm class, instead of overriding
  * [[querySerializer]] directly.
  *
  * @tparam PD Prepared data class.
  * @tparam M Trained model class.
  * @tparam Q Input query class.
  * @tparam P Output prediction class.
  * @group Algorithm
  */
abstract class LAlgorithm[PD, M : ClassTag, Q, P]
  extends BaseAlgorithm[RDD[PD], RDD[M], Q, P] {

  def trainBase(sc: SparkContext, pd: RDD[PD]): RDD[M] = pd.map(train)

  /** Implement this method to produce a model from prepared data.
    *
    * @param pd Prepared data for model training.
    * @return Trained model.
    */
  def train(pd: PD): M

  def batchPredictBase(sc: SparkContext, bm: Any, qs: RDD[(Long, Q)])
  : RDD[(Long, P)] = {
    val mRDD = bm.asInstanceOf[RDD[M]]
    batchPredict(mRDD, qs)
  }

  /** This is a default implementation to perform batch prediction. Override
    * this method for a custom implementation.
    *
    * @param mRDD A single model wrapped inside an RDD
    * @param qs An RDD of index-query tuples. The index is used to keep track of
    *           predicted results with corresponding queries.
    * @return Batch of predicted results
    */
  def batchPredict(mRDD: RDD[M], qs: RDD[(Long, Q)]): RDD[(Long, P)] = {
    val glomQs: RDD[Array[(Long, Q)]] = qs.glom()
    val cartesian: RDD[(M, Array[(Long, Q)])] = mRDD.cartesian(glomQs)
    cartesian.flatMap { case (m, qArray) =>
      qArray.map { case (qx, q) => (qx, predict(m, q)) }
    }
  }

  def predictBase(localBaseModel: Any, q: Q): P = {
    predict(localBaseModel.asInstanceOf[M], q)
  }

  /** Implement this method to produce a prediction from a query and trained
    * model.
    *
    * @param m Trained model produced by [[train]].
    * @param q An input query.
    * @return A prediction.
    */
  def predict(m: M, q: Q): P

  /** :: DeveloperApi ::
    * Engine developers should not use this directly (read on to see how local
    * algorithm models are persisted).
    *
    * Local algorithms produce local models. By default, models will be
    * serialized and stored automatically. Engine developers can override this behavior by
    * mixing the [[PersistentModel]] trait into the model class, and
    * PredictionIO will call [[PersistentModel.save]] instead. If it returns
    * true, a [[org.apache.predictionio.workflow.PersistentModelManifest]] will be
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
    // Check RDD[M].count == 1
    val m = bm.asInstanceOf[RDD[M]].first()
    if (m.isInstanceOf[PersistentModel[_]]) {
      if (m.asInstanceOf[PersistentModel[Params]].save(
        modelId, algoParams, sc)) {
        PersistentModelManifest(className = m.getClass.getName)
      } else {
        Unit
      }
    } else {
      m
    }
  }
}
