package org.template.recommendation

import org.apache.predictionio.controller.PDataSource
import org.apache.predictionio.controller.EmptyEvaluationInfo
import org.apache.predictionio.controller.EmptyActualResult
import org.apache.predictionio.controller.Params
import org.apache.predictionio.data.storage.{DataMap, Event, Storage}

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import grizzled.slf4j.Logger

case class DataSourceParams(appId: Int) extends Params

case class Item(creationYear: Option[Int])
object Item {
  object Fields {
    val CreationYear = "creationYear"
  }
}

class DataSource(val dsp: DataSourceParams)
  extends PDataSource[TrainingData,
      EmptyEvaluationInfo, Query, EmptyActualResult] {

  @transient lazy val logger = Logger[this.type]
  private lazy val EntityType = "movie"

  override
  def readTraining(sc: SparkContext): TrainingData = {
    val eventsDb = Storage.getPEvents()

    // create a RDD of (entityID, Item)
    // HOWTO: collecting items(movies)
    val itemsRDD = eventsDb.aggregateProperties(
      appId = dsp.appId,
      entityType = "item"
    )(sc).flatMap { case (entityId, properties) ⇒
      ItemMarshaller.unmarshall(properties).map(entityId → _)
    }

    // get all user rate events
    val rateEventsRDD: RDD[Event] = eventsDb.find(
      appId = dsp.appId,
      entityType = Some("user"),
      eventNames = Some(List("rate")), // read "rate"
      // targetEntityType is optional field of an event.
      targetEntityType = Some(Some(EntityType)))(sc)

    // collect ratings
    val ratingsRDD = rateEventsRDD.flatMap { event ⇒
      try {
        (event.event match {
          case "rate" => event.properties.getOpt[Double]("rating")
          case _ ⇒ None
        }).map(Rating(event.entityId, event.targetEntityId.get, _))
      } catch { case e: Exception ⇒
        logger.error(s"Cannot convert ${event} to Rating. Exception: ${e}.")
        throw e
      }
    }.cache()

    new TrainingData(ratingsRDD, itemsRDD)
  }
}

object ItemMarshaller {
  // HOWTO: implemented unmarshaller to collect properties for filtering.
  def unmarshall(properties: DataMap): Option[Item] =
    Some(Item(properties.getOpt[Int](Item.Fields.CreationYear)))
}

case class Rating(user: String, item: String, rating: Double)

class TrainingData(val ratings: RDD[Rating], val items: RDD[(String, Item)])
  extends Serializable {

  override def toString =
    s"ratings: [${ratings.count()}] (${ratings.take(2).toList}...)" +
    s"items: [${items.count()} (${items.take(2).toList}...)]"
}
