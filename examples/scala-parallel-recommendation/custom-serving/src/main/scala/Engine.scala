package org.template.recommendation

import io.prediction.controller.IEngineFactory
import io.prediction.controller.Engine

case class Query(
  user: String,
  num: Int
)

case class PredictedResult(
  itemScores: Array[ItemScore]
)

case class ItemScore(
  item: String,
  score: Double
)

object RecommendationEngine extends IEngineFactory {
  def apply() = {
    new Engine(
      classOf[DataSource],
      classOf[Preparator],
      Map("als" -> classOf[ALSAlgorithm]),
      classOf[Serving])
  }
}
