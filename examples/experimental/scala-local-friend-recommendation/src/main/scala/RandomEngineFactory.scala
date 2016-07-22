package org.apache.predictionio.examples.friendrecommendation

import org.apache.predictionio.controller._

object RandomEngineFactory extends IEngineFactory {
  override
  def apply() = {
    new Engine(
      classOf[FriendRecommendationDataSource],
      classOf[LIdentityPreparator[FriendRecommendationTrainingData]],
      Map("RandomAlgorithm" -> classOf[RandomAlgorithm]),
      classOf[LFirstServing[FriendRecommendationQuery, 
          FriendRecommendationPrediction]]
    )
  }
}
