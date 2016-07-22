package org.apache.predictionio.examples.friendrecommendation

import org.apache.predictionio.controller._

class FriendRecommendationDataSourceParams(
  val itemFilePath: String,
  val userKeywordFilePath: String,
  val userActionFilePath: String,
  val trainingRecordFilePath: String
) extends Params
