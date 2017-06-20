/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.predictionio.examples.similarproduct

import org.apache.predictionio.controller.{EmptyActualResult, EmptyEvaluationInfo, PDataSource, Params}
import org.apache.predictionio.data.store.PEventStore
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import grizzled.slf4j.Logger

case class DataSourceParams(appName: String) extends Params

class DataSource(val dsp: DataSourceParams)
  extends PDataSource[TrainingData,
      EmptyEvaluationInfo, Query, EmptyActualResult] {

  @transient lazy val logger = Logger[this.type]

  override
  def readTraining(sc: SparkContext): TrainingData = {

    // create a RDD of (entityID, User)
    val usersRDD: RDD[(String, User)] = PEventStore.aggregateProperties(
      appName = dsp.appName,
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
    val followEventsRDD: RDD[FollowEvent] = PEventStore.find(
      appName = dsp.appName,
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
