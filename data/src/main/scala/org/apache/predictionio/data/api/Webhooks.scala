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


package org.apache.predictionio.data.api

import akka.http.scaladsl.model.{FormData, StatusCode, StatusCodes}
import org.apache.predictionio.data.webhooks.ConnectorUtil
import org.apache.predictionio.data.storage.LEvents

import org.json4s.JObject

import akka.event.LoggingAdapter
import akka.actor.ActorSelection

import scala.concurrent.{ExecutionContext, Future}


private[predictionio] object Webhooks {

  def postJson(
    appId: Int,
    channelId: Option[Int],
    web: String,
    data: JObject,
    eventClient: LEvents,
    log: LoggingAdapter,
    stats: Boolean,
    statsActorRef: ActorSelection
  )(implicit ec: ExecutionContext): Future[(StatusCode, Map[String, String])] = {

    val eventFuture = Future {
      WebhooksConnectors.json.get(web).map { connector =>
        ConnectorUtil.toEvent(connector, data)
      }
    }

    eventFuture.flatMap {
      case None =>
        Future successful {
          val message = s"webhooks connection for ${web} is not supported."
          (StatusCodes.NotFound, Map("message" -> message))
        }
      case Some(event) =>
        val data = eventClient.futureInsert(event, appId, channelId).map { id =>
          val result = (StatusCodes.Created, Map("eventId" -> s"${id}"))

          if (stats) {
            statsActorRef ! Bookkeeping(appId, result._1, event)
          }
          result
        }
        data
    }
  }

  def getJson(
    appId: Int,
    channelId: Option[Int],
    web: String,
    log: LoggingAdapter
  )(implicit ec: ExecutionContext): Future[(StatusCode, Map[String, String])] = {
    Future {
      WebhooksConnectors.json.get(web).map { connector =>
        (StatusCodes.OK, Map("message" -> "Ok"))
      }.getOrElse {
        val message = s"webhooks connection for ${web} is not supported."
        (StatusCodes.NotFound, Map("message" -> message))
      }
    }
  }

  def postForm(
    appId: Int,
    channelId: Option[Int],
    web: String,
    data: FormData,
    eventClient: LEvents,
    log: LoggingAdapter,
    stats: Boolean,
    statsActorRef: ActorSelection
  )(implicit ec: ExecutionContext): Future[(StatusCode, Map[String, String])] = {
    val eventFuture = Future {
      WebhooksConnectors.form.get(web).map { connector =>
        ConnectorUtil.toEvent(connector, data.fields.toMap)
      }
    }

    eventFuture.flatMap {
      case None =>
        Future successful {
          val message = s"webhooks connection for ${web} is not supported."
          (StatusCodes.NotFound, Map("message" -> message))
        }
      case Some(event) =>
        val data = eventClient.futureInsert(event, appId, channelId).map { id =>
          val result = (StatusCodes.Created, Map("eventId" -> s"${id}"))

          if (stats) {
            statsActorRef ! Bookkeeping(appId, result._1, event)
          }
          result
        }
        data
    }
  }

  def getForm(
    appId: Int,
    channelId: Option[Int],
    web: String,
    log: LoggingAdapter
  )(implicit ec: ExecutionContext): Future[(StatusCode, Map[String, String])] = {
    Future {
      WebhooksConnectors.form.get(web).map { connector =>
        (StatusCodes.OK, Map("message" -> "Ok"))
      }.getOrElse {
        val message = s"webhooks connection for ${web} is not supported."
        (StatusCodes.NotFound, Map("message" -> message))
      }
    }
  }

}
