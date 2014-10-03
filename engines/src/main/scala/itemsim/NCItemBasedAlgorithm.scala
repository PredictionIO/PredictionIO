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

package io.prediction.engines.itemsim

import io.prediction.engines.base.mahout.NCItemBasedAlgorithmModel
import io.prediction.engines.base

import org.apache.mahout.cf.taste.common.TasteException
import org.apache.mahout.cf.taste.recommender.RecommendedItem

import grizzled.slf4j.Logger

import org.joda.time.DateTime

import scala.collection.JavaConversions._

case class NCItemBasedAlgorithmParams(
  val booleanData: Boolean = true,
  val itemSimilarity: String = "LogLikelihoodSimilarity",
  val weighted: Boolean = false,
  val threshold: Double = Double.MinPositiveValue,
  val freshness: Int = 0,
  val freshnessTimeUnit: Int = 86400,
  val recommendationTime: Option[Long] = Some(DateTime.now.getMillis)
) extends base.mahout.AbstractItemBasedAlgorithmParams {
  val unseenOnly: Boolean = false
  val nearestN: Int = 1
}

class NCItemBasedAlgorithm(params: NCItemBasedAlgorithmParams)
  extends base.mahout.AbstractNCItemBasedAlgorithm[Query, Prediction](params) {

  override
  def predict(model: NCItemBasedAlgorithmModel,
    query: Query): Prediction = {

    val recommender = model.recommender
    val itemIndexes: Seq[Long] = query.iids
      .map(model.itemsIndexMap.get(_)).flatten

    val rec: List[RecommendedItem] = if (itemIndexes.isEmpty) {
      logger.info(s"No index for ${query.iids}.")
      List()
    } else {
      try {
        if (params.freshness != 0)
          recommender.mostSimilarItems(itemIndexes.toArray, query.n,
            model.freshnessPairRescorer, false).toList
        else
          recommender.mostSimilarItems(itemIndexes.toArray, query.n, false)
            .toList
      } catch {
        case e: TasteException => {
          logger.info(s"Caught ${e} for query ${query}")
          List()
        }
        case e: Throwable => throw new RuntimeException(e)
      }
    }

    val items: Seq[(String, Double)] = rec.map { r =>
      val iid = model.validItemsMap(r.getItemID()).id
      (iid, r.getValue().toDouble)
    }

    new Prediction(
      items = items
    )
  }
}
