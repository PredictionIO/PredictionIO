package org.apache.predictionio.examples.pfriendrecommendation

import org.apache.predictionio.controller.EmptyParams
import org.apache.predictionio.controller.PPreparator
import org.apache.predictionio.controller.EmptyPreparatorParams

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import scala.io.Source // ADDED
import org.apache.predictionio.controller.Params // ADDED

 // ADDED CustomPreparatorParams case class
case class CustomPreparatorParams(
  val filepath: String
) extends Params

class IdentityPreparator(pp: EmptyPreparatorParams)
  extends PPreparator[TrainingData, TrainingData] {

  def prepare(sc: SparkContext, trainingData: TrainingData): TrainingData = {
    trainingData
  }
}
