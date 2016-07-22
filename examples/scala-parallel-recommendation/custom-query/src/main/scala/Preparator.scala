package org.template.recommendation

import org.apache.predictionio.controller.PPreparator

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

class Preparator extends PPreparator[TrainingData, PreparedData] {
  def prepare(sc: SparkContext, trainingData: TrainingData): PreparedData =
    new PreparedData(ratings = trainingData.ratings, items = trainingData.items)
}

// HOWTO: added items(movies) list to prepared data to have possiblity to sort
// them in predict stage.
class PreparedData(val ratings: RDD[Rating], val items: RDD[(String, Item)])
  extends Serializable
