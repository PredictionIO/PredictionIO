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

package org.apache.predictionio.examples.recommendation

import org.apache.predictionio.controller.PAlgorithm
import org.apache.predictionio.controller.Params
import org.apache.predictionio.data.storage.BiMap

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.recommendation.ALS
import org.apache.spark.mllib.recommendation.{Rating => MLlibRating}
import org.apache.spark.mllib.recommendation.ALSModel

import grizzled.slf4j.Logger

case class ALSAlgorithmParams(
  rank: Int,
  numIterations: Int,
  lambda: Double,
  seed: Option[Long]) extends Params

class ALSAlgorithm(val ap: ALSAlgorithmParams)
  extends PAlgorithm[PreparedData, ALSModel, Query, PredictedResult] {

  @transient lazy val logger = Logger[this.type]

  if (ap.numIterations > 30) {
    logger.warn(
      s"ALSAlgorithmParams.numIterations > 30, current: ${ap.numIterations}. " +
      s"There is a chance of running to StackOverflowException." +
      s"To remedy it, set lower numIterations or checkpoint parameters.")
  }

  def train(sc: SparkContext, data: PreparedData): ALSModel = {
    // MLLib ALS cannot handle empty training data.
    require(!data.ratings.take(1).isEmpty,
      s"RDD[Rating] in PreparedData cannot be empty." +
      " Please check if DataSource generates TrainingData" +
      " and Preparator generates PreparedData correctly.")
    // Convert user and item String IDs to Int index for MLlib

    val userStringIntMap = BiMap.stringInt(data.ratings.map(_.user))
    val itemStringIntMap = BiMap.stringInt(data.ratings.map(_.item))
    val mllibRatings = data.ratings.map( r =>
      // MLlibRating requires integer index for user and item
      MLlibRating(userStringIntMap(r.user), itemStringIntMap(r.item), r.rating)
    )

    // seed for MLlib ALS
    val seed = ap.seed.getOrElse(System.nanoTime)

    // Set checkpoint directory
    // sc.setCheckpointDir("checkpoint")

    // If you only have one type of implicit event (Eg. "view" event only),
    // set implicitPrefs to true
    // MODIFIED
    val implicitPrefs = true
    val als = new ALS()
    als.setUserBlocks(-1)
    als.setProductBlocks(-1)
    als.setRank(ap.rank)
    als.setIterations(ap.numIterations)
    als.setLambda(ap.lambda)
    als.setImplicitPrefs(implicitPrefs)
    als.setAlpha(1.0)
    als.setSeed(seed)
    als.setCheckpointInterval(10)
    val m = als.run(mllibRatings)

    new ALSModel(
      rank = m.rank,
      userFeatures = m.userFeatures,
      productFeatures = m.productFeatures,
      userStringIntMap = userStringIntMap,
      itemStringIntMap = itemStringIntMap)
  }

  def predict(model: ALSModel, query: Query): PredictedResult = {
    // Convert String ID to Int index for Mllib
    model.userStringIntMap.get(query.user).map { userInt =>
      // create inverse view of itemStringIntMap
      val itemIntStringMap = model.itemStringIntMap.inverse
      // recommendProducts() returns Array[MLlibRating], which uses item Int
      // index. Convert it to String ID for returning PredictedResult
      val itemScores = model.recommendProducts(userInt, query.num)
        .map (r => ItemScore(itemIntStringMap(r.product), r.rating))
      PredictedResult(itemScores)
    }.getOrElse{
      logger.info(s"No prediction for unknown user ${query.user}.")
      PredictedResult(Array.empty)
    }
  }

  // This function is used by the evaluation module, where a batch of queries is sent to this engine
  // for evaluation purpose.
  override def batchPredict(model: ALSModel, queries: RDD[(Long, Query)]): RDD[(Long, PredictedResult)] = {
    val userIxQueries: RDD[(Int, (Long, Query))] = queries
    .map { case (ix, query) => {
      // If user not found, then the index is -1
      val userIx = model.userStringIntMap.get(query.user).getOrElse(-1)
      (userIx, (ix, query))
    }}

    // Cross product of all valid users from the queries and products in the model.
    val usersProducts: RDD[(Int, Int)] = userIxQueries
      .keys
      .filter(_ != -1)
      .cartesian(model.productFeatures.map(_._1))

    // Call mllib ALS's predict function.
    val ratings: RDD[MLlibRating] = model.predict(usersProducts)

    // The following code construct predicted results from mllib's ratings.
    // Not optimal implementation. Instead of groupBy, should use combineByKey with a PriorityQueue
    val userRatings: RDD[(Int, Iterable[MLlibRating])] = ratings.groupBy(_.user)

    userIxQueries.leftOuterJoin(userRatings)
    .map {
      // When there are ratings
      case (userIx, ((ix, query), Some(ratings))) => {
        val topItemScores: Array[ItemScore] = ratings
        .toArray
        .sortBy(_.rating)(Ordering.Double.reverse) // note: from large to small ordering
        .take(query.num)
        .map { rating => ItemScore(
          model.itemStringIntMap.inverse(rating.product),
          rating.rating) }

        (ix, PredictedResult(itemScores = topItemScores))
      }
      // When user doesn't exist in training data
      case (userIx, ((ix, query), None)) => {
        require(userIx == -1)
        (ix, PredictedResult(itemScores = Array.empty))
      }
    }
  }
}
