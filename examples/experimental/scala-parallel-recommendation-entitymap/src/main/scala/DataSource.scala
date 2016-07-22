package org.template.recommendation

import org.apache.predictionio.controller.PDataSource
import org.apache.predictionio.controller.EmptyEvaluationInfo
import org.apache.predictionio.controller.EmptyActualResult
import org.apache.predictionio.controller.Params
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.Storage
import org.apache.predictionio.data.storage.EntityMap

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import grizzled.slf4j.Logger

case class DataSourceParams(appId: Int) extends Params

class DataSource(val dsp: DataSourceParams)
  extends PDataSource[TrainingData,
      EmptyEvaluationInfo, Query, EmptyActualResult] {

  @transient lazy val logger = Logger[this.type]

  override
  def readTraining(sc: SparkContext): TrainingData = {
    val eventsDb = Storage.getPEvents()

    val users: EntityMap[User] = eventsDb.extractEntityMap[User](
      appId = dsp.appId,
      entityType = "user",
      required = Some(Seq("attr0", "attr1", "attr2"))
    )(sc) { dm =>
      User(
        attr0 = dm.get[Double]("attr0"),
        attr1 = dm.get[Int]("attr1"),
        attr2 = dm.get[Int]("attr2")
      )
    }

    val items: EntityMap[Item] = eventsDb.extractEntityMap[Item](
      appId = dsp.appId,
      entityType = "item",
      required = Some(Seq("attrA", "attrB", "attrC"))
    )(sc) { dm =>
      Item(
        attrA = dm.get[String]("attrA"),
        attrB = dm.get[Int]("attrB"),
        attrC = dm.get[Boolean]("attrC")
      )
    }

    val eventsRDD: RDD[Event] = eventsDb.find(
      appId = dsp.appId,
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
    }
    new TrainingData(users, items, ratingsRDD)
  }
}

case class User(
  attr0: Double,
  attr1: Int,
  attr2: Int
)

case class Item(
  attrA: String,
  attrB: Int,
  attrC: Boolean
)

case class Rating(
  user: String,
  item: String,
  rating: Double
)

class TrainingData(
  val users: EntityMap[User],
  val items: EntityMap[Item],
  val ratings: RDD[Rating]
) extends Serializable {
  override def toString = {
    s"users: [${users.size} (${users.take(2).toString}...)]" +
    s"items: [${items.size} (${items.take(2).toString}...)]" +
    s"ratings: [${ratings.count()}] (${ratings.take(2).toList}...)"
  }
}
