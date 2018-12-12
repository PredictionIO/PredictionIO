/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, blocking}
import scala.language.postfixOps
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
      qArray.map {
        case (qx, q) =>
          (qx,
            Await.result(predictAsync(m, q)(scala.concurrent.ExecutionContext.global), 60 minutes) )
      }
    }
  }

  override def predictBaseAsync(localBaseModel: Any, q: Q)(implicit ec: ExecutionContext)
      : Future[P] =
    predictAsync(localBaseModel.asInstanceOf[M], q)(ec)

  @deprecated(message =
    "this method is just here for backward compatibility, predictBaseAsync() is called now",
    since = "0.14.0")
  override def predictBase(localBaseModel: Any, q: Q): P =
    predict(localBaseModel.asInstanceOf[M], q)

  /** Implement this method to produce a Future of a prediction in a non blocking way
    * from a query and trained model.
    *
    * This method is implemented to just delegate to blocking predict() for
    * backward compatibility reasons.
    * Definitely overwrite it to implement your blocking prediction method, and leave
    * the old blocking predict() as it is (throwing an exception), it won't be called from
    * now on.
    *
    * @param model Trained model produced by [[train]].
    * @param query An input query.
    * @param ec ExecutionContext to use for async operations
    * @return A Future of a prediction.
    */
  def predictAsync(model: M, query: Q)(implicit ec: ExecutionContext): Future[P] =
    Future.successful(blocking(predict(model, query)))

  /** Implement this method to produce a prediction from a query and trained
    * model.
    *
    * @param m Trained model produced by [[train]].
    * @param q An input query.
    * @return A prediction.
    */
  @deprecated(message = "override non blocking predictAsync() instead", since = "0.14.0")
  def predict(m: M, q: Q): P =
    throw new NotImplementedError("predict() is deprecated, override predictAsync() instead")

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
    m match {
      case m: PersistentModel[Params] @unchecked =>
        if(m.save(modelId, algoParams, sc)){
          PersistentModelManifest(className = m.getClass.getName)
        } else ()
      case _ => m
    }
  }
}
