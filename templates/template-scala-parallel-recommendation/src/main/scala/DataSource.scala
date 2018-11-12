package org.example.recommendation

import org.apache.predictionio.controller.PDataSource
import org.apache.predictionio.controller.EmptyEvaluationInfo
import org.apache.predictionio.controller.Params
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.store.PEventStore

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import grizzled.slf4j.Logger

case class DataSourceEvalParams(kFold: Int, queryNum: Int)

case class DataSourceParams(
  appName: String,
  evalParams: Option[DataSourceEvalParams]) extends Params

class DataSource(val dsp: DataSourceParams)
  extends PDataSource[TrainingData,
      EmptyEvaluationInfo, Query, ActualResult] {

  @transient lazy val logger = Logger[this.type]

  def getRatings(sc: SparkContext): RDD[Rating] = {

    val eventsRDD: RDD[Event] = PEventStore.find(
      appName = dsp.appName,
      entityType = Some("user"),
      eventNames = Some(List("rate", "buy")), // read "rate" and "buy" event
      // targetEntityType is optional field of an event.
      targetEntityType = Some(Some("item")))(sc)

    val ratingsRDD: RDD[Rating] = eventsRDD.map { event =>
      val rating = try {
        val ratingValue: Double = event.event match {
          case "rate" => event.properties.get[Double]("rating")
          case "buy" => 4.0 // map buy event to rating value of 4
          case _ => throw new Exception(s"Unexpected event ${event} is read.")
        }
        // entityId and targetEntityId is String
        Rating(event.entityId,
          event.targetEntityId.get,
          ratingValue)
      } catch {
        case e: Exception => {
          logger.error(s"Cannot convert ${event} to Rating. Exception: ${e}.")
          throw e
        }
      }
      rating
    }.cache()

    ratingsRDD
  }

  override
  def readTraining(sc: SparkContext): TrainingData = {
    new TrainingData(getRatings(sc))
  }

  override
  def readEval(sc: SparkContext)
  : Seq[(TrainingData, EmptyEvaluationInfo, RDD[(Query, ActualResult)])] = {
    require(!dsp.evalParams.isEmpty, "Must specify evalParams")
    val evalParams = dsp.evalParams.get

    val kFold = evalParams.kFold
    val ratings: RDD[(Rating, Long)] = getRatings(sc).zipWithUniqueId
    ratings.cache

    (0 until kFold).map { idx => {
      val trainingRatings = ratings.filter(_._2 % kFold != idx).map(_._1)
      val testingRatings = ratings.filter(_._2 % kFold == idx).map(_._1)

      val testingUsers: RDD[(String, Iterable[Rating])] = testingRatings.groupBy(_.user)

      (new TrainingData(trainingRatings),
        new EmptyEvaluationInfo(),
        testingUsers.map {
          case (user, ratings) => (Query(user, evalParams.queryNum), ActualResult(ratings.toArray))
        }
      )
    }}
  }
}

case class Rating(
  user: String,
  item: String,
  rating: Double
)

class TrainingData(
  val ratings: RDD[Rating]
) extends Serializable {
  override def toString = {
    s"ratings: [${ratings.count()}] (${ratings.take(2).toList}...)"
  }
}
