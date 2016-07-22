package org.apache.predictionio.examples.experimental.trimapp

import org.apache.predictionio.controller.P2LAlgorithm
import org.apache.predictionio.controller.Params

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import grizzled.slf4j.Logger

//case class AlgorithmParams(mult: Int) extends Params

//class Algorithm(val ap: AlgorithmParams)
class Algorithm
  extends P2LAlgorithm[TrainingData, Model, Query, PredictedResult] {

  @transient lazy val logger = Logger[this.type]

  def train(data: TrainingData): Model = {
    new Model
  }

  def predict(model: Model, query: Query): PredictedResult = {
    // Prefix the query with the model data
    PredictedResult(p = "")
  }
}

class Model extends Serializable {
  override def toString = "Model"
}
