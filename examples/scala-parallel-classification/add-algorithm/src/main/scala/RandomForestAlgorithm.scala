package org.template.classification

import org.apache.predictionio.controller.P2LAlgorithm
import org.apache.predictionio.controller.Params

import org.apache.spark.SparkContext
import org.apache.spark.mllib.tree.RandomForest // CHANGED
import org.apache.spark.mllib.tree.model.RandomForestModel // CHANGED
import org.apache.spark.mllib.linalg.Vectors

// CHANGED
case class RandomForestAlgorithmParams(
  numClasses: Int,
  numTrees: Int,
  featureSubsetStrategy: String,
  impurity: String,
  maxDepth: Int,
  maxBins: Int
) extends Params

// extends P2LAlgorithm because the MLlib's RandomForestModel doesn't
// contain RDD.
class RandomForestAlgorithm(val ap: RandomForestAlgorithmParams) // CHANGED
  extends P2LAlgorithm[PreparedData, RandomForestModel, // CHANGED
  Query, PredictedResult] {

  // CHANGED
  def train(sc: SparkContext, data: PreparedData): RandomForestModel = {
    // CHANGED
    // Empty categoricalFeaturesInfo indicates all features are continuous.
    val categoricalFeaturesInfo = Map[Int, Int]()
    RandomForest.trainClassifier(
      data.labeledPoints,
      ap.numClasses,
      categoricalFeaturesInfo,
      ap.numTrees,
      ap.featureSubsetStrategy,
      ap.impurity,
      ap.maxDepth,
      ap.maxBins)
  }

  def predict(
    model: RandomForestModel, // CHANGED
    query: Query): PredictedResult = {

    val label = model.predict(Vectors.dense(query.features))
    new PredictedResult(label)
  }

}
