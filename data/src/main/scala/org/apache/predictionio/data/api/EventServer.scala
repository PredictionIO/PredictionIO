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

import akka.event.{Logging, LoggingAdapter}
import sun.misc.BASE64Decoder
import java.util.concurrent.TimeUnit

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{FormData, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.directives._
import akka.http.scaladsl.server._
import akka.pattern.ask
import akka.util.Timeout
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import org.apache.predictionio.data.storage._
import org.apache.predictionio.akkahttpjson4s.Json4sSupport._
import org.json4s.{DefaultFormats, Formats, JObject}

import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object Json4sProtocol {
  implicit val serialization = org.json4s.native.Serialization
  implicit def json4sFormats: Formats = DefaultFormats +
    new EventJson4sSupport.APISerializer +
    new BatchEventsJson4sSupport.APISerializer +
    // NOTE: don't use Json4s JodaTimeSerializers since it has issues,
    // some format not converted, or timezone not correct
    new DateTimeJson4sSupport.Serializer
}

case class EventServerConfig(
  ip: String = "localhost",
  port: Int = 7070,
  plugins: String = "plugins",
  stats: Boolean = false)

object EventServer {
  import Json4sProtocol._
  import FutureDirectives._
  import Common._

  private val MaxNumberOfEventsPerBatchRequest = 50
  private lazy val base64Decoder = new BASE64Decoder
  private implicit val timeout = Timeout(5, TimeUnit.SECONDS)
  private case class AuthData(appId: Int, channelId: Option[Int], events: Seq[String])

  private def FailedAuth[T]: Either[Rejection, T] = Left(
    AuthenticationFailedRejection(
      AuthenticationFailedRejection.CredentialsRejected, HttpChallenge("eventserver", None)
    )
  )

  private def MissedAuth[T]: Either[Rejection, T] = Left(
    AuthenticationFailedRejection(
      AuthenticationFailedRejection.CredentialsMissing, HttpChallenge("eventserver", None)
    )
  )

  def createRoute(eventClient: LEvents,
                  accessKeysClient: AccessKeys,
                  channelsClient: Channels,
                  logger: LoggingAdapter,
                  statsActorRef: ActorSelection,
                  pluginsActorRef: ActorSelection,
                  config: EventServerConfig)(implicit executionContext: ExecutionContext): Route = {

    /* with accessKey in query/header, return appId if succeed */
    def withAccessKey: RequestContext => Future[Either[Rejection, AuthData]] = {
      ctx: RequestContext =>
        val accessKeyParamOpt = ctx.request.uri.query().get("accessKey")
        val channelParamOpt = ctx.request.uri.query().get("channel")
        Future {
          // with accessKey in query, return appId if succeed
          accessKeyParamOpt.map { accessKeyParam =>
            accessKeysClient.get(accessKeyParam).map { k =>
              channelParamOpt.map { ch =>
                val channelMap =
                  channelsClient.getByAppid(k.appid)
                    .map(c => (c.name, c.id)).toMap
                if (channelMap.contains(ch)) {
                  Right(AuthData(k.appid, Some(channelMap(ch)), k.events))
                } else {
                  Left(ChannelRejection(s"Invalid channel '$ch'."))
                }
              }.getOrElse{
                Right(AuthData(k.appid, None, k.events))
              }
            }.getOrElse(FailedAuth)
          }.getOrElse {
            // with accessKey in header, return appId if succeed
            ctx.request.headers.find(_.name == "Authorization").map { authHeader =>
              authHeader.value.split("Basic ") match {
                case Array(_, value) =>
                  val appAccessKey =
                    new String(base64Decoder.decodeBuffer(value)).trim.split(":")(0)
                  accessKeysClient.get(appAccessKey) match {
                    case Some(k) => Right(AuthData(k.appid, None, k.events))
                    case None => FailedAuth
                  }

                case _ => FailedAuth
              }
            }.getOrElse(MissedAuth)
          }
        }
    }

    def authenticate[T](authenticator: RequestContext => Future[Either[Rejection, T]]):
        AuthenticationDirective[T] = {
      handleRejections(rejectionHandler).tflatMap { _ =>
        extractRequestContext.flatMap { requestContext =>
          onSuccess(authenticator(requestContext)).flatMap {
            case Right(x) => provide(x)
            case Left(x)  => reject(x): Directive1[T]
          }
        }
      }
    }

    val pluginContext = EventServerPluginContext(logger)
    val jsonPath = """(.+)\.json$""".r
    val formPath = """(.+)\.form$""".r

    val route: Route =
      pathSingleSlash {
        get {
          complete(Map("status" -> "alive"))
        }
      } ~
      path("plugins.json") {
        get {
          complete(
            Map("plugins" -> Map(
              "inputblockers" -> pluginContext.inputBlockers.map { case (n, p) =>
                n -> Map(
                  "name"        -> p.pluginName,
                  "description" -> p.pluginDescription,
                  "class"       -> p.getClass.getName)
              },
              "inputsniffers" -> pluginContext.inputSniffers.map { case (n, p) =>
                n -> Map(
                  "name"        -> p.pluginName,
                  "description" -> p.pluginDescription,
                  "class"       -> p.getClass.getName)
              }
            ))
          )
        }
      } ~
      path("plugins" / Segments) { segments =>
        get {
          handleExceptions(exceptionHandler) {
            authenticate(withAccessKey) { authData =>
              val pluginArgs = segments.drop(2)
              val pluginType = segments(0)
              val pluginName = segments(1)
              pluginType match {
                case EventServerPlugin.inputBlocker =>
                  complete(HttpResponse(entity = HttpEntity(
                    `application/json`,
                    pluginContext.inputBlockers(pluginName).handleREST(
                      authData.appId,
                      authData.channelId,
                      pluginArgs)
                  )))

                case EventServerPlugin.inputSniffer =>
                  complete(pluginsActorRef ? PluginsActor.HandleREST(
                    appId = authData.appId,
                    channelId = authData.channelId,
                    pluginName = pluginName,
                    pluginArgs = pluginArgs) map { json =>
                      HttpResponse(entity = HttpEntity(
                        `application/json`,
                        json.asInstanceOf[String]
                      ))
                    })
              }
            }
          }
        }
      } ~
      path("events" / jsonPath ) { eventId =>
        get {
          handleExceptions(exceptionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              val channelId = authData.channelId
              logger.debug(s"GET event ${eventId}.")
              onSuccess(eventClient.futureGet(eventId, appId, channelId)){ eventOpt =>
                  eventOpt.map { event =>
                    complete(StatusCodes.OK, event)
                  }.getOrElse(
                    complete(StatusCodes.NotFound, Map("message" -> "Not Found"))
                  )
              }
            }
          }
        } ~
        delete {
          handleExceptions(exceptionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              val channelId = authData.channelId
              logger.debug(s"DELETE event ${eventId}.")
              onSuccess(eventClient.futureDelete(eventId, appId, channelId)){ found =>
                if (found) {
                  complete(StatusCodes.OK, Map("message" -> "Found"))
                } else {
                  complete(StatusCodes.NotFound, Map("message" -> "Not Found"))
                }
              }
            }
          }
        }
      } ~
      path("events.json") {
        post {
          handleExceptions(exceptionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              val channelId = authData.channelId
              val events = authData.events
              entity(as[Event]) { event =>
                if (events.isEmpty || authData.events.contains(event.event)) {
                  pluginContext.inputBlockers.values.foreach(
                    _.process(EventInfo(
                      appId = appId,
                      channelId = channelId,
                      event = event), pluginContext))
                  onSuccess(eventClient.futureInsert(event, appId, channelId)){ id =>
                    pluginsActorRef ! EventInfo(
                      appId = appId,
                      channelId = channelId,
                      event = event)
                    val result = (StatusCodes.Created, Map("eventId" -> s"${id}"))
                    if (config.stats) {
                      statsActorRef ! Bookkeeping(appId, result._1, event)
                    }
                    complete(result)
                  }
                } else {
                  complete(StatusCodes.Forbidden,
                    Map("message" -> s"${event.event} events are not allowed"))
                }
              }
            }
          }
        } ~
        get {
          handleExceptions(exceptionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              val channelId = authData.channelId
              parameters(
                'startTime.?,
                'untilTime.?,
                'entityType.?,
                'entityId.?,
                'event.?,
                'targetEntityType.?,
                'targetEntityId.?,
                'limit.as[Int].?,
                'reversed.as[Boolean].?) {
                (startTimeStr, untilTimeStr, entityType, entityId,
                eventName,  // only support one event name
                targetEntityType, targetEntityId,
                limit, reversed) =>
                  logger.debug(
                    s"GET events of appId=${appId} " +
                    s"st=${startTimeStr} ut=${untilTimeStr} " +
                    s"et=${entityType} eid=${entityId} " +
                    s"li=${limit} rev=${reversed} ")

                  require(!((reversed == Some(true))
                    && (entityType.isEmpty || entityId.isEmpty)),
                    "the parameter reversed can only be used with" +
                      " both entityType and entityId specified.")

                  val parseTime = Future {
                    val startTime = startTimeStr.map(Utils.stringToDateTime(_))
                    val untilTime = untilTimeStr.map(Utils.stringToDateTime(_))
                    (startTime, untilTime)
                  }


                  val f = parseTime.flatMap { case (startTime, untilTime) =>
                    val data = eventClient.futureFind(
                      appId = appId,
                      channelId = channelId,
                      startTime = startTime,
                      untilTime = untilTime,
                      entityType = entityType,
                      entityId = entityId,
                      eventNames = eventName.map(List(_)),
                      targetEntityType = targetEntityType.map(Some(_)),
                      targetEntityId = targetEntityId.map(Some(_)),
                      limit = limit.orElse(Some(20)),
                      reversed = reversed)
                      .map { eventIter =>
                        if (eventIter.hasNext) {
                          (StatusCodes.OK, eventIter.toArray)
                        } else {
                          (StatusCodes.NotFound, Map("message" -> "Not Found"))
                        }
                      }
                    data
                  }

                  onSuccess(f){ (status, body) => complete(status, body) }
                }
            }
          }
        }
      } ~
      path("batch" / "events.json") {
        post {
          handleExceptions(exceptionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              val channelId = authData.channelId
              val allowedEvents = authData.events

              entity(as[Seq[Try[Event]]]) { events =>
                if (events.length <= MaxNumberOfEventsPerBatchRequest) {
                  val eventWithIndex = events.zipWithIndex

                  val taggedEvents = eventWithIndex.collect { case (Success(event), i) =>
                    if(allowedEvents.isEmpty || allowedEvents.contains(event.event)){
                      (Right(event), i)
                    } else {
                      (Left(event), i)
                    }
                  }

                  val insertEvents = taggedEvents.collect { case (Right(event), i) =>
                    (event, i)
                  }

                  insertEvents.foreach { case (event, i) =>
                    pluginContext.inputBlockers.values.foreach(
                      _.process(EventInfo(
                        appId = appId,
                        channelId = channelId,
                        event = event), pluginContext))
                  }

                  val f: Future[Seq[Map[String, Any]]] = eventClient.futureInsertBatch(
                    insertEvents.map(_._1), appId, channelId).map { insertResults =>
                    val results = insertResults.zip(insertEvents).map { case (id, (event, i)) =>
                      pluginsActorRef ! EventInfo(
                        appId = appId,
                        channelId = channelId,
                        event = event)
                      val status = StatusCodes.Created
                      if (config.stats) {
                        statsActorRef ! Bookkeeping(appId, status, event)
                      }
                      (Map(
                        "status"  -> status.intValue,
                        "eventId" -> s"${id}"), i)
                    } ++
                      // Results of denied events
                      taggedEvents.collect { case (Left(event), i) =>
                        (Map(
                          "status"  -> StatusCodes.Forbidden.intValue,
                          "message" -> s"${event.event} events are not allowed"), i)
                      } ++
                      // Results of failed to deserialze events
                      eventWithIndex.collect { case (Failure(exception), i) =>
                        (Map(
                          "status"  -> StatusCodes.BadRequest.intValue,
                          "message" -> s"${exception.getMessage()}"), i)
                      }

                    // Restore original order
                    results.sortBy { case (_, i) => i }.map { case (data, _) => data }
                  }

                  onSuccess(f.recover { case exception =>
                    Map(
                      "status" -> StatusCodes.InternalServerError.intValue,
                      "message" -> s"${exception.getMessage()}"
                    )
                  }){ res => complete(res) }

                } else {
                  complete(StatusCodes.BadRequest,
                    Map("message" -> (s"Batch request must have less than or equal to " +
                      s"${MaxNumberOfEventsPerBatchRequest} events")))
                }
              }
            }
          }
        }
      } ~
      path("stats.json") {
        get {
          handleExceptions(exceptionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              if (config.stats) {
                complete {
                  statsActorRef ? GetStats(appId) map {
                    _.asInstanceOf[Map[String, StatsSnapshot]]
                  }
                }
              } else {
                complete(
                  StatusCodes.NotFound,
                  Map("message" -> "To see stats, launch Event Server with --stats argument.")
                )
              }
            }
          }
        }  // stats.json get
      } ~
      path("webhooks" / jsonPath ) { web =>
        post {
          handleExceptions(exceptionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              val channelId = authData.channelId
              entity(as[JObject]) { jObj =>
                onSuccess(Webhooks.postJson(
                  appId = appId,
                  channelId = channelId,
                  web = web,
                  data = jObj,
                  eventClient = eventClient,
                  log = logger,
                  stats = config.stats,
                  statsActorRef = statsActorRef
                )){
                  (status, body) => complete(status, body)
                }
              }
            }
          }
        } ~
        get {
          handleExceptions(exceptionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              val channelId = authData.channelId
              onSuccess(
                Webhooks.getJson(
                appId = appId,
                channelId = channelId,
                web = web,
                log = logger)
              ){
                (status, body) => complete(status, body)
              }
            }
          }
        }
      } ~
      path("webhooks" / formPath ) { web =>
        post {
          handleExceptions(exceptionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              val channelId = authData.channelId
              entity(as[FormData]){ formData =>
                logger.debug(formData.toString)
                onSuccess(Webhooks.postForm(
                  appId = appId,
                  channelId = channelId,
                  web = web,
                  data = formData,
                  eventClient = eventClient,
                  log = logger,
                  stats = config.stats,
                  statsActorRef = statsActorRef
                )){
                  (status, body) => complete(status, body)
                }
              }
            }
          }
        } ~
        get {
          handleExceptions(exceptionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              val channelId = authData.channelId
              onSuccess(Webhooks.getForm(
                appId = appId,
                channelId = channelId,
                web = web,
                log = logger
              )){
                (status, body) => complete(status, body)
              }
            }
          }
        }
      }

    route
  }

  def createEventServer(config: EventServerConfig): ActorSystem = {
    implicit val system = ActorSystem("EventServerSystem")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val eventClient = Storage.getLEvents()
    val accessKeysClient = Storage.getMetaDataAccessKeys()
    val channelsClient = Storage.getMetaDataChannels()

    val statsActorRef = system.actorSelection("/user/StatsActor")
    val pluginsActorRef = system.actorSelection("/user/PluginsActor")

    val logger = Logging(system, getClass)

    val route = createRoute(eventClient, accessKeysClient, channelsClient,
      logger, statsActorRef, pluginsActorRef, config)

    Http().bindAndHandle(route, config.ip, config.port)

    system
  }
}

object Run {
  def main(args: Array[String]): Unit = {
    val f = EventServer.createEventServer(EventServerConfig(
      ip = "0.0.0.0",
      port = 7070))
    .whenTerminated

    Await.ready(f, Duration.Inf)
  }
}
