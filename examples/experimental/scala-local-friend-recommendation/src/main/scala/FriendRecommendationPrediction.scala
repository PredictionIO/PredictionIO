package org.apache.predictionio.examples.friendrecommendation

class FriendRecommendationPrediction (
  val confidence: Double,
  // returning boolean acceptance to align with KDD 2012 scenario
  val acceptance: Boolean
) extends Serializable
