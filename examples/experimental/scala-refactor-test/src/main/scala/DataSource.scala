package pio.refactor

import org.apache.predictionio.controller.PDataSource
import org.apache.predictionio.controller.EmptyEvaluationInfo
import org.apache.predictionio.controller.EmptyActualResult
import org.apache.predictionio.controller.Params
import org.apache.predictionio.controller._
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.Storage

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import grizzled.slf4j.Logger

//case class DataSourceParams(appId: Int) extends Params

class DataSource
  extends PDataSource[
      TrainingData,
      EmptyEvaluationInfo, 
      Query, 
      ActualResult] {

  @transient lazy val logger = Logger[this.type]

  override
  def readTraining(sc: SparkContext): TrainingData = {
    new TrainingData(
      events = sc.parallelize(0 until 100)
    )
  }

  override
  def readEval(sc: SparkContext)
  : Seq[(TrainingData, EmptyEvaluationInfo, RDD[(Query, ActualResult)])] =
  {
    logger.error("Datasource!!!")
    (0 until 3).map { ex => 
      (
        readTraining(sc),
        new EmptyEvaluationInfo(),
        sc
        .parallelize((0 until 20)
          .map {i => (Query(i), new ActualResult())}))
    }
  }
}

class TrainingData(
  val events: RDD[Int]
) extends Serializable {
  override def toString = {
    s"events: [${events.count()}] (${events.take(2).toList}...)"
  }
}
