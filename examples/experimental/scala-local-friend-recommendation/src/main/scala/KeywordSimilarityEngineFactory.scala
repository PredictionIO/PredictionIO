package org.apache.predictionio.examples.friendrecommendation

import org.apache.predictionio.controller._

object KeywordSimilarityEngineFactory extends IEngineFactory {
  override
  def apply() = {
    new Engine(
      classOf[FriendRecommendationDataSource],
      classOf[LIdentityPreparator[FriendRecommendationTrainingData]],
      Map("KeywordSimilarityAlgorithm" -> classOf[KeywordSimilarityAlgorithm]),
      classOf[LFirstServing[FriendRecommendationQuery,
          FriendRecommendationPrediction]]
    )
  }
}
