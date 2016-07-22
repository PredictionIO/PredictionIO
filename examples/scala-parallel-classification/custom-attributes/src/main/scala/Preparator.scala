package com.test1

import org.apache.predictionio.controller.PPreparator

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.regression.LabeledPoint

class PreparedData(
  val labeledPoints: RDD[LabeledPoint],
  val gendersMap: Map[String,Double],
  val educationMap: Map[String,Double]
) extends Serializable

class Preparator extends PPreparator[TrainingData, PreparedData] {

  def prepare(sc: SparkContext, trainingData: TrainingData): PreparedData = {
    new PreparedData(
      trainingData.labeledPoints,
      trainingData.gendersMap,
      trainingData.educationMap)
  }
}
