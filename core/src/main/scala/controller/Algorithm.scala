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
import io.prediction.core.LModelAlgorithm

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import scala.reflect._
import scala.reflect.runtime.universe._

/** Base class of a local algorithm.
  *
  * A local algorithm runs locally within a single machine and produces a model
  * that can fit within a single machine.
  *
  * @tparam AP Algorithm parameters class.
  * @tparam PD Prepared data class.
  * @tparam M Trained model class.
  * @tparam Q Input query class.
  * @tparam P Output prediction class.
  * @group Algorithm
  */
abstract class LAlgorithm[
    AP <: Params : ClassTag,
    PD,
    M : ClassTag,
    Q : Manifest,
    P]
  extends BaseAlgorithm[AP, RDD[PD], RDD[M], Q, P]
  with LModelAlgorithm[M, Q, P] {

  /** Do not use directly or override this method, as this is called by
    * PredictionIO workflow to train a model.
    */
  def trainBase(sc: SparkContext, pd: RDD[PD]): RDD[M] = pd.map(train)

  /** Implement this method to produce a model from prepared data.
    *
    * @param pd Prepared data for model training.
    * @return Trained model.
    */
  def train(pd: PD): M

  /** Implement this method to produce a prediction from a query and trained
    * model.
    *
    * @param model Trained model produced by [[train]].
    * @param query An input query.
    * @return A prediction.
    */
  def predict(model: M, query: Q): P
}

/** Base class of a parallel-to-local algorithm.
  *
  * A parallel-to-local algorithm can be run in parallel on a cluster and
  * produces a model that can fit within a single machine.
  *
  * @tparam AP Algorithm parameters class.
  * @tparam PD Prepared data class.
  * @tparam M Trained model class.
  * @tparam Q Input query class.
  * @tparam P Output prediction class.
  * @group Algorithm
  */
abstract class P2LAlgorithm[
    AP <: Params : ClassTag, PD, M : ClassTag, Q : Manifest, P]
  extends BaseAlgorithm[AP, PD, RDD[M], Q, P]
  with LModelAlgorithm[M, Q, P] {

  /** Do not use directly or override this method, as this is called by
    * PredictionIO workflow to train a model.
    *
    * Developer note:
    * In train: PD => M, M is a local object. We have to parallelize it.
    */
  def trainBase(sc: SparkContext, pd: PD): RDD[M] = {
    val m: M = train(pd)
    sc.parallelize(Array(m))
  }

  /** Implement this method to produce a model from prepared data.
    *
    * @param pd Prepared data for model training.
    * @return Trained model.
    */
  def train(pd: PD): M

  /** Implement this method to produce a prediction from a query and trained
    * model.
    *
    * @param model Trained model produced by [[train]].
    * @param query An input query.
    * @return A prediction.
    */
  def predict(model: M, query: Q): P
}

/** Base class of a parallel algorithm.
  *
  * A parallel algorithm can be run in parallel on a cluster and produces a
  * model that can also be distributed across a cluster.
  *
  * @tparam AP Algorithm parameters class.
  * @tparam PD Prepared data class.
  * @tparam M Trained model class.
  * @tparam Q Input query class.
  * @tparam P Output prediction class.
  * @group Algorithm
  */
abstract class PAlgorithm[AP <: Params : ClassTag, PD, M, Q : Manifest, P]
  extends BaseAlgorithm[AP, PD, M, Q, P] {

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
  def batchPredictBase(baseModel: Any, indexedQueries: RDD[(Long, Q)])
  : RDD[(Long, P)] = {
    batchPredict(baseModel.asInstanceOf[M], indexedQueries)
  }

  /** Implement this method to produce predictions from batch queries.
    *
    * Evaluation call this method. Since in PAlgorithm, M may contain RDDs, it
    * is impossible to call `predict(model, query)` without localizing queries
    * (which is very inefficient). Hence, engine builders using PAlgorithm need
    * to implement this method for evaluation purpose.
    *
    * @param model Trained model produced by [[train]].
    * @param indexedQueries Batch of queries with indices.
    * @return An RDD of indexed predictions.
    */
  def batchPredict(model: M, indexedQueries: RDD[(Long, Q)]): RDD[(Long, P)]

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
}
