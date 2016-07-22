package org.apache.predictionio.examples.friendrecommendation

import org.apache.predictionio.controller._

// For random algorithm
import scala.util.Random

class RandomAlgorithm (val ap: FriendRecommendationAlgoParams)
  extends LAlgorithm[FriendRecommendationTrainingData,
    RandomModel, FriendRecommendationQuery, FriendRecommendationPrediction] {

  override
  def train(pd: FriendRecommendationTrainingData): RandomModel = {
    new RandomModel(0.5) 
  }

  override
  def predict(model: RandomModel, query: FriendRecommendationQuery): 
    FriendRecommendationPrediction = {
    val randomConfidence = Random.nextDouble
    val acceptance = randomConfidence >= model.randomThreshold
    new FriendRecommendationPrediction(randomConfidence, acceptance)
  }
}
