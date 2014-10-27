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

import io.prediction.controller.LAlgorithm
import io.prediction.controller.Params
import io.prediction.core.BaseAlgorithm

import net.jodah.typetools.TypeResolver

import org.apache.spark.SparkContext
import org.apache.spark.api.java.JavaPairRDD
import org.apache.spark.api.java.function.PairFunction
import org.apache.spark.rdd.RDD

import scala.reflect._

/**
 * Base class of a local algorithm.
 *
 * A local algorithm runs locally within a single machine and produces a model
 * that can fit within a single machine.
 *
 * @tparam AP     Algorithm Parameters
 * @tparam PD     Prepared Data
 * @tparam M      Model
 * @tparam Q      Input Query
 * @tparam P      Output Prediction
 */
abstract class LJavaAlgorithm[AP <: Params, PD, M, Q, P]
  extends LAlgorithm[AP, PD, M, Q, P]()(
    JavaUtils.fakeClassTag[AP],
    JavaUtils.fakeClassTag[M],
    JavaUtils.fakeManifest[Q]) {
  def train(pd: PD): M

  def predict(model: M, query: Q): P

  /** Returns a Class object of Q for internal use. */
  def queryClass(): Class[Q] = {
    val typeArgs = TypeResolver.resolveRawArguments(
      classOf[LJavaAlgorithm[AP, PD, M, Q, P]],
      getClass)
    typeArgs(3).asInstanceOf[Class[Q]]
  }
}

/** Base class of a parallel algorithm.
  *
  * A parallel algorithm can be run in parallel on a cluster and produces a
  * model that can also be distributed across a cluster.
  *
  * @tparam AP     Algorithm parameters class.
  * @tparam PD     Prepared data class.
  * @tparam M      Trained model class.
  * @tparam Q      Input query class.
  * @tparam P      Output prediction class.
  */
abstract class PJavaAlgorithm[AP <: Params, PD, M, Q, P]
  extends BaseAlgorithm[AP, PD, M, Q, P]()(
    JavaUtils.fakeClassTag[AP],
    JavaUtils.fakeManifest[Q]) {

  /** Do not use directly or override this method, as this is called by
    * PredictionIO workflow to train a model.
    */
  def trainBase(sc: SparkContext, pd: PD): M = train(pd)

  /** Implement this method to produce a model from prepared data.
    *
    * @param pd Prepared data for model training.
    * @return Trained model.
    */
  def train(pd: PD): M

  /** Do not use directly or override this method, as this is called by
    * PredictionIO workflow to perform batch prediction.
    */
  override def batchPredictBase(baseModel: Any, indexedQueries: RDD[(Long, Q)])
  : RDD[(Long, P)] = {
    implicit val fakeQTag: ClassTag[Q] = JavaUtils.fakeClassTag[Q]
    batchPredict(baseModel.asInstanceOf[M],
        JavaPairRDD.fromRDD(indexedQueries)).rdd
  }

  /** Implement this method to produce predictions from batch queries.
    *
    * Evaluation call this method. Since in PJavaAlgorithm, M may contain RDDs,
    * it is impossible to call `predict(model, query)` without localizing
    * queries (which is very inefficient). Hence, engine builders using
    * PJavaAlgorithm need to implement this method for evaluation purpose.
    *
    * @param model Trained model produced by [[train]].
    * @param indexedQueries Batch of queries with indices.
    * @return An RDD of indexed predictions.
    */
  def batchPredict(model: M, indexedQueries: JavaPairRDD[Long, Q])
  : JavaPairRDD[Long, P]

  /** Do not use directly or override this method, as this is called by
    * PredictionIO workflow to perform prediction.
    */
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

  /** Returns a Class object of Q for internal use. */
  def queryClass(): Class[Q] = {
    val typeArgs = TypeResolver.resolveRawArguments(
      classOf[PJavaAlgorithm[AP, PD, M, Q, P]],
      getClass)
    typeArgs(3).asInstanceOf[Class[Q]]
  }
}
