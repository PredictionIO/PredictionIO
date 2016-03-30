package io.prediction.examples.pfriendrecommendation

import io.prediction.controller.IEngineFactory
import io.prediction.controller.Engine

case class Query(
  val item1: Long,
  val item2: Long
)

case class PredictedResult(
  val productScores: Array[ProductScore]
)

case class ProductScore(
  product: Int,
  score: Double
)

object PSimRankEngineFactory extends IEngineFactory {
  def apply() = {
    Engine(
      Map(
        "default" -> classOf[DataSource],
        "node" -> classOf[NodeSamplingDataSource],
        "forest" -> classOf[ForestFireSamplingDataSource]),
      classOf[IdentityPreparator],
      Map("simrank" -> classOf[SimRankAlgorithm]),
      classOf[Serving])
  }
}
