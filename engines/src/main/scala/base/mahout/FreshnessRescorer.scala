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

import org.apache.mahout.cf.taste.recommender.IDRescorer
import org.apache.mahout.cf.taste.recommender.Rescorer
import org.apache.mahout.common.LongPair

import org.joda.time.DateTime

import grizzled.slf4j.Logger

object RescoreUtils {

  def rescoreByStarttime(freshness: Int, freshnessTimeUnit: Long,
    starttime: Long, originalScore: Double,
    recommendationTimeOpt: Option[Long]): Double = {

    val recommendationTime = recommendationTimeOpt.getOrElse(
      DateTime.now.getMillis)

    if (freshness > 0) {
      val timeDiff = (recommendationTime - starttime) / 1000 / freshnessTimeUnit
      if (timeDiff > 0)
        originalScore * scala.math.exp(-timeDiff / (11 - freshness))
      else
        originalScore
    } else originalScore
  }

}

class FreshnessIDRescorer(freshness: Int, recommendationTimeOpt: Option[Long],
  freshnessTimeUnit: Long,
  itemsMap: Map[Long, ItemModel]) extends IDRescorer {

  @transient lazy val logger = Logger[this.type]

  logger.info("Building FreshnessIDRescorer...")

  def isFiltered(id: Long): Boolean = false

  def rescore(id: Long, originalScore: Double): Double = {

    itemsMap.get(id).map { i =>
      RescoreUtils.rescoreByStarttime(freshness, freshnessTimeUnit,
        i.starttime, originalScore,
        recommendationTimeOpt)
    }.getOrElse(originalScore)
    /*
    val recommendationTime = recommendationTimeOpt.getOrElse(
      DateTime.now.millis)

    if (freshness > 0) {
      itemsMap.get(id) map { i =>
        val timeDiff = (recommendationTime - i.starttime) / 1000 /
          freshnessTimeUnit
        if (timeDiff > 0)
          originalScore * scala.math.exp(-timeDiff / (11 - freshness))
        else
          originalScore
      } getOrElse originalScore
    } else originalScore
    */
  }
}

class FreshnessPairRescorer(freshness: Int,
  recommendationTimeOpt: Option[Long],
  freshnessTimeUnit: Long,
  itemsMap: Map[Long, ItemModel]) extends Rescorer[LongPair] {

  @transient lazy val logger = Logger[this.type]

  logger.info("Building FreshnessPairRescorer...")

  def isFiltered(pair: LongPair): Boolean = false

  def rescore(pair: LongPair, originalScore: Double): Double = {
    // second is the targetting item
    itemsMap.get(pair.getSecond()).map { i =>
      RescoreUtils.rescoreByStarttime(freshness, freshnessTimeUnit,
        i.starttime, originalScore,
        recommendationTimeOpt)
    }.getOrElse(originalScore)
  }
}
