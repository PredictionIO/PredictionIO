package org.apache.predictionio.examples.friendrecommendation

class FriendRecommendationQuery (
  // To align with the KDD 2012 scenario
  // Given a user and an item, predict acceptance
  val user: Int,
  val item: Int
) extends Serializable
