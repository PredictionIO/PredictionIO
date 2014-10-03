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

package io.prediction.engines.itemrank.mahout

import io.prediction.controller.Params
import io.prediction.controller.LAlgorithm
import io.prediction.engines.util.MahoutUtil

import io.prediction.engines.base.PreparedData
import io.prediction.engines.itemrank.Query
import io.prediction.engines.itemrank.Prediction

import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import scala.collection.JavaConversions._

import scala.collection.mutable.PriorityQueue

import org.joda.time.DateTime

import org.apache.mahout.cf.taste.common.Weighting
import org.apache.mahout.cf.taste.similarity.ItemSimilarity
import org.apache.mahout.cf.taste.model.DataModel
import org.apache.mahout.cf.taste.model.Preference
import org.apache.mahout.cf.taste.model.PreferenceArray
import org.apache.mahout.cf.taste.impl.model.GenericDataModel
import org.apache.mahout.cf.taste.impl.model.GenericBooleanPrefDataModel
import org.apache.mahout.cf.taste.impl.model.GenericPreference
import org.apache.mahout.cf.taste.impl.model.GenericUserPreferenceArray
import org.apache.mahout.cf.taste.impl.model.file.FileDataModel
import org.apache.mahout.cf.taste.impl.common.FastByIDMap
import org.apache.mahout.cf.taste.impl.common.FastIDSet

import org.apache.mahout.cf.taste.impl.similarity.{
  CityBlockSimilarity,
  EuclideanDistanceSimilarity,
  LogLikelihoodSimilarity,
  PearsonCorrelationSimilarity,
  TanimotoCoefficientSimilarity,
  UncenteredCosineSimilarity
}

class ItemBasedAlgoParams(
  val booleanData: Boolean,
  val itemSimilarity: String,
  val weighted: Boolean,
  val nearestN: Int,
  val threshold: Double,
  val numSimilarItems: Int,
  val numUserActions: Int,
  val freshness: Int,
  val freshnessTimeUnit: Int, // seconds
  val recommendationTime: Option[Long]
) extends Params {}

class ItemBasedModel(
  val userHistory: Map[String, Set[(String, Int)]],
  val itemSim: Map[String, Seq[(String, Double)]]
) extends Serializable {
  override
  def toString = { s"${userHistory.take(2).mapValues(_.take(2))}" +
    s"${itemSim.take(2).mapValues(_.take(2))}" +
    s"..."
  }
}


class ItemBasedAlgorithm(params: ItemBasedAlgoParams)
  extends LAlgorithm[ItemBasedAlgoParams, PreparedData,
  ItemBasedModel, Query, Prediction] {

  var _minScore = 0.0

  override def train(
    preparedData: PreparedData): ItemBasedModel = {
      val numSimilarItems: Int = params.numSimilarItems
      val recommendationTime: Long = params.recommendationTime.getOrElse(
        DateTime.now.getMillis
      )
      val freshness = params.freshness
      // in seconds
      val freshnessTimeUnit: Int = params.freshnessTimeUnit // 86400 (1 day)

      val dataModel: DataModel = if (params.booleanData) {
        MahoutUtil.buildBooleanPrefDataModel(preparedData.rating.map { r =>
          (r.uindex, r.iindex, r.t) })
      } else {
        MahoutUtil.buildDataModel(preparedData.rating.map{ r =>
          (r.uindex, r.iindex, r.rating.toFloat, r.t) })
      }
      val similarity: ItemSimilarity = buildItemSimilarity(dataModel)

      val itemIds = dataModel.getItemIDs.toSeq.map(_.toLong)
      val itemsMap = preparedData.items
      val usersMap = preparedData.users
      val validItemsSet = preparedData.items.keySet

      val allTopScores = itemIds.par.map { iid =>
        val simScores = validItemsSet.toSeq
          .map { simiid =>
            val originalScore = similarity.itemSimilarity(iid, simiid)
            val score = if (freshness > 0) {
              itemsMap.get(simiid) map { item =>
                item.starttime.map { starttime =>
                  val timeDiff = (recommendationTime - starttime) / 1000.0 /
                    freshnessTimeUnit
                  if (timeDiff > 0)
                    originalScore * scala.math.exp(-timeDiff / (11 - freshness))
                  else
                    originalScore
                } getOrElse originalScore
              } getOrElse originalScore
            } else originalScore
            (simiid, score)
          }
          // filter out invalid score or the same iid itself, < threshold
          .filter { x: (Int, Double) => (!x._2.isNaN()) && (x._1 != iid) &&
            (x._2 >= params.threshold) }

        (iid.toInt, getTopN(simScores, numSimilarItems)(ScoreOrdering.reverse))
      }.toMap

      val itemSimMap = allTopScores.seq.map {
        case (iindex, topScores) =>
          val topScoresIid = topScores.map{ case (simindex, score) =>
            (itemsMap(simindex).iid, score)
          }
          (itemsMap(iindex).iid, topScoresIid)
      }

      val userHistoryMap = preparedData.rating.groupBy(_.uindex)
        .map {
          case (k, listOfRating) =>
            val history = listOfRating.sortBy(_.t)
              // take latest actions at the end
              .takeRight(params.numUserActions)
              .map(r => (itemsMap(r.iindex).iid, r.rating))

            (usersMap(k).uid, history.toSet)
        }

      new ItemBasedModel (
        userHistory = userHistoryMap,
        itemSim = itemSimMap
      )
  }

  override def predict(model: ItemBasedModel,
    query: Query): Prediction = {
      val nearestK = params.nearestN
      val uid = query.uid
      val possibleItems = query.iids
      val history: Map[String, Int] = model.userHistory.getOrElse(uid, Set())
        .toMap

      val itemsScore = possibleItems.toSeq.map { iid =>
        val score = if (model.itemSim.contains(iid) && (!history.isEmpty)) {
          val sims: Seq[(String, Double)] = model.itemSim(iid)
          val rank = sims.filter { case (iid, score) => history.contains(iid) }
            .take(nearestK)
            .map { case (iid, score) => (score * history(iid)) }
            .sum
          Some(rank)
        } else {
          None
        }
        (iid, score)
      }

      // keep same order as input if no score
      val (itemsWithScore, itemsNoScore) = itemsScore.partition(_._2 != None)
      val sorted = itemsWithScore.map{case(iid, score) => (iid, score.get)}
        .sortBy(_._2)(Ordering.Double.reverse)

      val rankedItems = sorted ++
        (itemsNoScore.map{ case (iid, score) => (iid, _minScore) })

      val isOriginal = (itemsWithScore.size == 0)

      new Prediction (
        items = rankedItems,
        isOriginal = isOriginal
      )
    }

  object ScoreOrdering extends Ordering[(Int, Double)] {
    override def compare(a: (Int, Double), b: (Int, Double)) =
      a._2 compare b._2
  }

  private def getTopN[T](s: Seq[T], n: Int)(ord: Ordering[T]): Seq[T] = {
    val q = PriorityQueue()(ord)

    for (x <- s) {
      if (q.size < n)
        q.enqueue(x)
      else {
        // q is full
        if (ord.compare(x, q.head) < 0) {
          q.dequeue()
          q.enqueue(x)
        }
      }
    }

    q.dequeueAll.toSeq.reverse
  }

  // return Mahout's ItemSimilarity object
  private def buildItemSimilarity(dataModel: DataModel): ItemSimilarity = {

    val booleanData: Boolean = params.booleanData
    val itemSimilarity: String = params.itemSimilarity
    val weighted: Boolean = params.weighted

    val weightedParam: Weighting = if (weighted)
      Weighting.WEIGHTED
    else
      Weighting.UNWEIGHTED

    val similarity: ItemSimilarity = itemSimilarity match {
      case "CityBlockSimilarity" => new CityBlockSimilarity(dataModel)
      case "EuclideanDistanceSimilarity" =>
        new EuclideanDistanceSimilarity(dataModel, weightedParam)
      case "LogLikelihoodSimilarity" => new LogLikelihoodSimilarity(dataModel)
      case "PearsonCorrelationSimilarity" =>
        new PearsonCorrelationSimilarity(dataModel, weightedParam)
      case "TanimotoCoefficientSimilarity" =>
        new TanimotoCoefficientSimilarity(dataModel)
      case "UncenteredCosineSimilarity" =>
        new UncenteredCosineSimilarity(dataModel, weightedParam)
      case _ => throw new RuntimeException(
        "Invalid ItemSimilarity: " + itemSimilarity)
    }

    similarity
  }

}
