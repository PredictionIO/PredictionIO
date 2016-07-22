package org.template.recommendeduser

import org.apache.predictionio.controller.PPreparator
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

class Preparator
  extends PPreparator[TrainingData, PreparedData] {

  def prepare(sc: SparkContext, trainingData: TrainingData): PreparedData = {
    new PreparedData(
      users = trainingData.users,
      followEvents = trainingData.followEvents)
  }
}

class PreparedData(
  val users: RDD[(String, User)],
  val followEvents: RDD[FollowEvent]
) extends Serializable
