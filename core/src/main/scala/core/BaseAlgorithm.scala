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

package io.prediction.core

import io.prediction.controller.Params
import io.prediction.controller.Utils

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import scala.reflect._

// FIXME. The name collides with current BaseAlgorithm. Will remove once the
// code is completely revamped.

abstract class BaseAlgorithm[AP <: Params : ClassTag, PD, M, Q : Manifest, P]
  extends AbstractDoer[AP] {
  def trainBase(sc: SparkContext, pd: PD): M

  def batchPredictBase(baseModel: Any, baseQueries: RDD[(Long, Q)])
  : RDD[(Long, P)]

  // One Prediction
  def predictBase(baseModel: Any, query: Q): P

  def queryManifest(): Manifest[Q] = manifest[Q]

  @transient lazy val querySerializer = Utils.json4sDefaultFormats
}

trait LModelAlgorithm[M, Q, P] {
  def getModel(baseModel: Any): RDD[Any] = {
    baseModel.asInstanceOf[RDD[Any]]
  }

  def batchPredictBase(baseModel: Any, baseQueries: RDD[(Long, Q)])
  : RDD[(Long, P)] = {
    val rddModel: RDD[M] = baseModel.asInstanceOf[RDD[M]].coalesce(1)
    val rddQueries: RDD[(Long, Q)] = baseQueries.coalesce(1)

    rddModel.zipPartitions(rddQueries)(batchPredictWrapper)
  }

  def batchPredictWrapper(model: Iterator[M], queries: Iterator[(Long, Q)])
  : Iterator[(Long, P)] = {
    batchPredict(model.next, queries)
  }

  // Expected to be overridden for performance consideration
  def batchPredict(model: M, queries: Iterator[(Long, Q)])
  : Iterator[(Long, P)] = {
    queries.map { case (idx, q) => (idx, predict(model, q)) }
  }

  // One Prediction
  def predictBase(localBaseModel: Any, query: Q): P = {
    predict(localBaseModel.asInstanceOf[M], query)
  }

  def predict(model: M, query: Q): P
}
