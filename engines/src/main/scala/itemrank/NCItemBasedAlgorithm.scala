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

package io.prediction.engines.itemrank

import io.prediction.controller.Params
import io.prediction.controller.LAlgorithm

import io.prediction.engines.util.MahoutUtil
import io.prediction.engines.base.mahout.KNNItemBasedRecommender
import io.prediction.engines.base.mahout.AllValidItemsCandidateItemsStrategy
import io.prediction.engines.base.PreparedData
import io.prediction.engines.base.mahout.NCItemBasedAlgorithmModel
import io.prediction.engines.base

import org.apache.mahout.cf.taste.model.DataModel
import org.apache.mahout.cf.taste.common.Weighting
import org.apache.mahout.cf.taste.recommender.Recommender
import org.apache.mahout.cf.taste.recommender.IDRescorer
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

import grizzled.slf4j.Logger

import com.github.nscala_time.time.Imports._

import scala.collection.JavaConversions._

case class NCItemBasedAlgorithmParams(
  val booleanData: Boolean = true,
  val itemSimilarity: String = "LogLikelihoodSimilarity",
  val weighted: Boolean = false,
  val threshold: Double = Double.MinPositiveValue,
  val nearestN: Int = 10,
  //val unseenOnly: Boolean = false,
  val freshness: Int = 0,
  val freshnessTimeUnit: Int = 86400,
  val recommendationTime: Option[Long] = Some(DateTime.now.millis)
) extends base.mahout.AbstractItemBasedAlgorithmParams {
  val unseenOnly = false
}

class NCItemBasedAlgorithm(params: NCItemBasedAlgorithmParams)
  extends base.mahout.AbstractNCItemBasedAlgorithm[Query, Prediction](params) {


  override
  def predict(model: NCItemBasedAlgorithmModel,
    query: Query): Prediction = {

    val recommender = model.recommender
    val rec: Seq[(String, Double)] = model.usersMap.get(query.uid)
      .map { user =>
        val uindex = user.index
        // List[RecommendedItem] // getItemID(), getValue()
        try {
          query.iids.map { iid =>
            val score = model.itemsIndexMap.get(iid).map { iindex =>
              val p = recommender.estimatePreference(uindex, iindex).toDouble
              model.freshnessRescorer.rescore(iindex, p)
            }.getOrElse{ Double.NaN }
            (iid, score)
          }
        } catch {
          case e: NoSuchUserException => {
            logger.info(
              s"NoSuchUserException ${query.uid} (index ${uindex}) in model.")
            query.iids.map((_, Double.NaN))
          }
          case e: Throwable => throw new RuntimeException(e)
        }
      }.getOrElse{
        logger.info(s"Unknow user id ${query.uid}")
        query.iids.map((_, Double.NaN))
      }

    // order of withoutScore is preserved
    val (withScore, withoutScore) = rec.partition(!_._2.isNaN)
    val ranked = withScore.sortBy(_._2).reverse

    new Prediction(
      items = (ranked ++ withoutScore),
      isOriginal = (ranked.size == 0)
    )
  }

  /*override
  def predict(model: NCItemBasedAlgorithmModel,
    query: Query): Prediction = {

    val recommender = model.recommender
    val rec: List[RecommendedItem] = model.usersMap.get(query.uid)
      .map { user =>
        val uindex = user.index
        // List[RecommendedItem] // getItemID(), getValue()
        try {
          if (params.freshness != 0)
            recommender.recommend(uindex, model.itemCount,
              model.freshnessRescorer).toList
          else
            recommender.recommend(uindex, model.itemCount).toList
        } catch {
          case e: NoSuchUserException => {
            logger.info(
              s"NoSuchUserException ${query.uid} (index ${uindex}) in model.")
            List()
          }
          case e: Throwable => throw new RuntimeException(e)
        }
      }.getOrElse{
        logger.info(s"Unknow user id ${query.uid}")
        List()
      }

    val all: Seq[(String, Double)] = rec.map { r =>
      val iid = model.validItemsMap(r.getItemID()).id
      (iid, r.getValue().toDouble)
    }
    // following adopted from 0.7 serving output logic

    // If no recommendations found for this user, simply return the original
    // list.
    val iids = query.iids
    val iidsSet = iids.toSet
    val ranked: Seq[(String, Double)] = all.filter( r =>
      iidsSet(r._1)
    )

    //val (rankedIids, rankedScores) = ranked.unzip
    val rankedIidsSet = ranked.map(_._1).toSet
    val unranked = iids.filterNot(x => rankedIidsSet(x))

    new Prediction(
      items = (ranked ++ (unranked.map(i => (i, Double.NaN)))),
      isOriginal = (ranked.size == 0)
    )
  }*/

}
