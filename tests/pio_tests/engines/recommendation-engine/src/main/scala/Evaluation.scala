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

package org.template.recommendation

import org.apache.predictionio.controller.Evaluation
import org.apache.predictionio.controller.OptionAverageMetric
import org.apache.predictionio.controller.AverageMetric
import org.apache.predictionio.controller.EmptyEvaluationInfo
import org.apache.predictionio.controller.EngineParamsGenerator
import org.apache.predictionio.controller.EngineParams
import org.apache.predictionio.controller.MetricEvaluator

// Usage:
// $ pio eval org.template.recommendation.RecommendationEvaluation \
//   org.template.recommendation.EngineParamsList

case class PrecisionAtK(k: Int, ratingThreshold: Double = 2.0)
    extends OptionAverageMetric[EmptyEvaluationInfo, Query, PredictedResult, ActualResult] {
  require(k > 0, "k must be greater than 0")

  override def header = s"Precision@K (k=$k, threshold=$ratingThreshold)"

  def calculate(q: Query, p: PredictedResult, a: ActualResult): Option[Double] = {
    val positives: Set[String] = a.ratings.filter(_.rating >= ratingThreshold).map(_.item).toSet

    // If there is no positive results, Precision is undefined. We don't consider this case in the
    // metrics, hence we return None.
    if (positives.size == 0) {
      return None
    }

    val tpCount: Int = p.itemScores.take(k).filter(is => positives(is.item)).size

    Some(tpCount.toDouble / math.min(k, positives.size))
  }
}

case class PositiveCount(ratingThreshold: Double = 2.0)
    extends AverageMetric[EmptyEvaluationInfo, Query, PredictedResult, ActualResult] {
  override def header = s"PositiveCount (threshold=$ratingThreshold)"

  def calculate(q: Query, p: PredictedResult, a: ActualResult): Double = {
    a.ratings.filter(_.rating >= ratingThreshold).size
  }
}

object RecommendationEvaluation extends Evaluation {
  engineEvaluator = (
    RecommendationEngine(),
    MetricEvaluator(
      metric = PrecisionAtK(k = 10, ratingThreshold = 4.0),
      otherMetrics = Seq(
        PositiveCount(ratingThreshold = 4.0),
        PrecisionAtK(k = 10, ratingThreshold = 2.0),
        PositiveCount(ratingThreshold = 2.0),
        PrecisionAtK(k = 10, ratingThreshold = 1.0),
        PositiveCount(ratingThreshold = 1.0)
      )))
}


object ComprehensiveRecommendationEvaluation extends Evaluation {
  val ratingThresholds = Seq(0.0, 2.0, 4.0)
  val ks = Seq(1, 3, 10)

  engineEvaluator = (
    RecommendationEngine(),
    MetricEvaluator(
      metric = PrecisionAtK(k = 3, ratingThreshold = 2.0),
      otherMetrics = (
        (for (r <- ratingThresholds) yield PositiveCount(ratingThreshold = r)) ++
        (for (r <- ratingThresholds; k <- ks) yield PrecisionAtK(k = k, ratingThreshold = r))
      )))
}


trait BaseEngineParamsList extends EngineParamsGenerator {
  protected val baseEP = EngineParams(
    dataSourceParams = DataSourceParams(
      appName = "INVALID_APP_NAME",
      evalParams = Some(DataSourceEvalParams(kFold = 5, queryNum = 10))))
}

object EngineParamsList extends BaseEngineParamsList {
  engineParamsList = for(
    rank <- Seq(5, 10, 20);
    numIterations <- Seq(1, 5, 10))
    yield baseEP.copy(
      algorithmParamsList = Seq(
        ("als", ALSAlgorithmParams(rank, numIterations, 0.01, Some(3)))))
}
