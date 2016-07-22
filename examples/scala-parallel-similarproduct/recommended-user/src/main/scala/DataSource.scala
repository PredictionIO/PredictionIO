package org.template.recommendeduser

import grizzled.slf4j.Logger
import org.apache.predictionio.controller.{EmptyActualResult, EmptyEvaluationInfo, PDataSource, Params}
import org.apache.predictionio.data.storage.Storage
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

case class DataSourceParams(appId: Int) extends Params

class DataSource(val dsp: DataSourceParams)
  extends PDataSource[TrainingData,
      EmptyEvaluationInfo, Query, EmptyActualResult] {

  @transient lazy val logger = Logger[this.type]

  override
  def readTraining(sc: SparkContext): TrainingData = {
    val eventsDb = Storage.getPEvents()

    // create a RDD of (entityID, User)
    val usersRDD: RDD[(String, User)] = eventsDb.aggregateProperties(
      appId = dsp.appId,
      entityType = "user"
    )(sc).map { case (entityId, properties) =>
      val user = try {
        User()
      } catch {
        case e: Exception => {
          logger.error(s"Failed to get properties $properties of" +
            s" user $entityId. Exception: $e.")
          throw e
        }
      }
      (entityId, user)
    }.cache()

    // get all "user" "follow" "followedUser" events
    val followEventsRDD: RDD[FollowEvent] = eventsDb.find(
      appId = dsp.appId,
      entityType = Some("user"),
      eventNames = Some(List("follow")),
      // targetEntityType is optional field of an event.
      targetEntityType = Some(Some("user")))(sc)
      // eventsDb.find() returns RDD[Event]
      .map { event =>
        val followEvent = try {
          event.event match {
            case "follow" => FollowEvent(
              user = event.entityId,
              followedUser = event.targetEntityId.get,
              t = event.eventTime.getMillis)
            case _ => throw new Exception(s"Unexpected event $event is read.")
          }
        } catch {
          case e: Exception => {
            logger.error(s"Cannot convert $event to FollowEvent." +
              s" Exception: $e.")
            throw e
          }
        }
        followEvent
      }.cache()

    new TrainingData(
      users = usersRDD,
      followEvents = followEventsRDD
    )
  }
}

case class User()

case class FollowEvent(user: String, followedUser: String, t: Long)

class TrainingData(
  val users: RDD[(String, User)],
  val followEvents: RDD[FollowEvent]
) extends Serializable {
  override def toString = {
    s"users: [${users.count()} (${users.take(2).toList}...)]" +
    s"followEvents: [${followEvents.count()}] (${followEvents.take(2).toList}...)"
  }
}
