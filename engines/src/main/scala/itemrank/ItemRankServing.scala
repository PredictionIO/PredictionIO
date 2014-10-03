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

import io.prediction.controller.LServing
import io.prediction.controller.EmptyParams
import breeze.stats.{ mean, meanAndVariance, MeanAndVariance }

// Only return first prediction
class ItemRankServing extends LServing[EmptyParams, Query, Prediction] {
  override def serve(query: Query, predictions: Seq[Prediction]): Prediction = {
    predictions.head
  }
}

// Return average item score for each non-original prediction.
class ItemRankAverageServing extends LServing[EmptyParams, Query, Prediction] {
  override def serve(query: Query, prediction: Seq[Prediction]): Prediction = {
    // Only consider non-original items
    val itemsList: Seq[Seq[(String, Double)]] = prediction
      .filter(!_.isOriginal)
      .map(_.items)

    val validCount = itemsList.size
    if (validCount == 0) {
      return new Prediction(
        items = query.iids.map(e => (e, 0.0)),
        isOriginal = true)
    }

    // mvc : mean, variance, count
    val mvcList: Seq[MeanAndVariance] = itemsList
      .map { l => meanAndVariance(l.map(_._2)) }

    val querySize = query.iids.size

    val items: Seq[(String, Double)] = (0 until validCount)
    .flatMap { i =>
      val items = itemsList(i)
      if (items.size != querySize) {
        throw new Exception(
          s"Prediction $i has size ${items.size} != query $querySize")
      }

      items.map(e => (e._1, (e._2 - mvcList(i).mean) / mvcList(i).stdDev))
    }
    .groupBy(_._1)  // group by item
    .mapValues(l => mean(l.map(_._2)))
    .toSeq
    .sortBy(-_._2)

    new Prediction(items, false)
  }
}
