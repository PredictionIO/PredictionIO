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

package io.prediction.engines.base.mahout

import io.prediction.controller.Params
import io.prediction.controller.LAlgorithm

import io.prediction.engines.util.MahoutUtil
//import io.prediction.engines.base.mahout.KNNItemBasedRecommender
//import io.prediction.engines.base.mahout.AllValidItemsCandidateItemsStrategy
import io.prediction.engines.base.PreparedData

import org.apache.mahout.cf.taste.model.DataModel
import org.apache.mahout.cf.taste.common.Weighting
import org.apache.mahout.cf.taste.recommender.Recommender
import org.apache.mahout.cf.taste.recommender.ItemBasedRecommender
import org.apache.mahout.cf.taste.recommender.IDRescorer
import org.apache.mahout.cf.taste.recommender.Rescorer
import org.apache.mahout.cf.taste.similarity.ItemSimilarity
import org.apache.mahout.cf.taste.impl.similarity.{
  CityBlockSimilarity,
  EuclideanDistanceSimilarity,
  LogLikelihoodSimilarity,
  PearsonCorrelationSimilarity,
  TanimotoCoefficientSimilarity,
  UncenteredCosineSimilarity
}
import org.apache.mahout.cf.taste.common.NoSuchUserException
import org.apache.mahout.cf.taste.recommender.RecommendedItem
import org.apache.mahout.common.LongPair

import grizzled.slf4j.Logger

import org.joda.time.DateTime

import scala.collection.JavaConversions._

abstract class AbstractItemBasedAlgorithmParams extends Params {
  val booleanData: Boolean
  val itemSimilarity: String
  val weighted: Boolean
  val threshold: Double
  val nearestN: Int
  val unseenOnly: Boolean
  val freshness: Int
  val freshnessTimeUnit: Int
  val recommendationTime: Option[Long]
}

case class ItemModel(
  val id: String,
  val starttime: Long
) extends Serializable

case class UserModel(
  val index: Long
) extends Serializable

class NCItemBasedAlgorithmModel(
  val dataModel: DataModel,
  val seenDataModel: DataModel,
  val validItemsMap: Map[Long, ItemModel],
  val usersMap: Map[String, UserModel],
  val itemCount: Int, // TODO: delete this
  val params: AbstractItemBasedAlgorithmParams
) extends Serializable {

  @transient lazy val logger = Logger[this.type]
  @transient lazy val recommender: ItemBasedRecommender = buildRecommender()
  @transient lazy val freshnessRescorer: IDRescorer = new FreshnessIDRescorer(
      params.freshness,
      params.recommendationTime,
      params.freshnessTimeUnit,
      validItemsMap)
  @transient lazy val freshnessPairRescorer: Rescorer[LongPair] =
    new FreshnessPairRescorer(
      params.freshness,
      params.recommendationTime,
      params.freshnessTimeUnit,
      validItemsMap)

  // string id -> long index
  @transient lazy val itemsIndexMap: Map[String, Long] = validItemsMap.map {
      case (index, item) => (item.id, index) }

  // TODO: refactor to suppport other types of Recommender (eg. SVD)
  private def buildRecommender(): ItemBasedRecommender = {
    logger.info("Building recommender...")
    val weightedParam: Weighting = if (params.weighted) Weighting.WEIGHTED
      else Weighting.UNWEIGHTED

    val similarity: ItemSimilarity = params.itemSimilarity match {
      case "CityBlockSimilarity" =>
        new CityBlockSimilarity(dataModel)
      case "EuclideanDistanceSimilarity" =>
        new EuclideanDistanceSimilarity(dataModel, weightedParam)
      case "LogLikelihoodSimilarity" => new LogLikelihoodSimilarity(dataModel)
      case "PearsonCorrelationSimilarity" =>
        new PearsonCorrelationSimilarity(dataModel, weightedParam)
      case "TanimotoCoefficientSimilarity" =>
        new TanimotoCoefficientSimilarity(dataModel)
      case "UncenteredCosineSimilarity" =>
        new UncenteredCosineSimilarity(dataModel, weightedParam)
      case _ => throw new RuntimeException("Invalid ItemSimilarity: " +
        params.itemSimilarity)
    }

    val candidateItemsStrategy = if (params.unseenOnly)
      new AllValidItemsCandidateItemsStrategy(validItemsMap.keySet.toArray,
        seenDataModel)
    else
      new AllValidItemsCandidateItemsStrategy(validItemsMap.keySet.toArray)

    val recommender: ItemBasedRecommender = new KNNItemBasedRecommender(
      dataModel,
      similarity,
      candidateItemsStrategy,
      params.booleanData,
      params.nearestN,
      params.threshold)

    recommender
  }

  override def toString(): String = {
    val usersStr = usersMap.keysIterator.take(3)
      .mkString(s"Users: (${usersMap.size}) [", ",", ",...]")
    val itemsStr = validItemsMap.keysIterator.take(3)
      .mkString(s"Items: (${validItemsMap.size}) [", ",", ",...]")

    s"${this.getClass().getCanonicalName()}\n$usersStr\n$itemsStr"
  }

}

abstract class AbstractNCItemBasedAlgorithm[Q : Manifest, P](
  params: AbstractItemBasedAlgorithmParams)
  extends LAlgorithm[AbstractItemBasedAlgorithmParams, PreparedData,
  NCItemBasedAlgorithmModel, Q, P] {

  @transient lazy val logger = Logger[this.type]
  @transient lazy val recommendationTime = params.recommendationTime.getOrElse(
    DateTime.now.getMillis)

  override def train(preparedData: PreparedData): NCItemBasedAlgorithmModel = {

    val dataModel: DataModel = if (params.booleanData) {
      MahoutUtil.buildBooleanPrefDataModel(preparedData.rating.map { r =>
        (r.uindex, r.iindex, r.t) })
    } else {
      MahoutUtil.buildDataModel(preparedData.rating.map{ r =>
        (r.uindex, r.iindex, r.rating.toFloat, r.t) })
    }

    // don't have seperated seen actions data for now
    val seenDataModel: DataModel = preparedData.seenU2IActions.map {
      seenU2IActions =>
        if (seenU2IActions.isEmpty)
          null
        else
          MahoutUtil.buildBooleanPrefDataModel(seenU2IActions.map { a =>
            (a.uindex, a.iindex, a.t) })
    }.getOrElse(dataModel)

    val validItemsMap: Map[Long, ItemModel] = preparedData.items
      .map{ case (k, v) =>
        (k.toLong, ItemModel(
          v.iid,
          v.starttime.getOrElse(recommendationTime))
        )
      }
    val usersMap: Map[String, UserModel] = preparedData.users
      .map {case (k,v) =>
        (v.uid, UserModel(k.toLong))
      }
    val itemCount = preparedData.items.size

    new NCItemBasedAlgorithmModel(
      dataModel,
      seenDataModel,
      validItemsMap,
      usersMap,
      itemCount,
      params
    )
  }

}
