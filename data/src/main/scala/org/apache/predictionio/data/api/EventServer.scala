/** Copyright 2015 TappingStone, Inc.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package org.apache.predictionio.data.api

import akka.event.Logging
import sun.misc.BASE64Decoder

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import org.apache.predictionio.data.Utils
import org.apache.predictionio.data.storage.AccessKeys
import org.apache.predictionio.data.storage.Channels
import org.apache.predictionio.data.storage.DateTimeJson4sSupport
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.EventJson4sSupport
import org.apache.predictionio.data.storage.BatchEventsJson4sSupport
import org.apache.predictionio.data.storage.LEvents
import org.apache.predictionio.data.storage.Storage
import org.json4s.DefaultFormats
import org.json4s.Formats
import org.json4s.JObject
import org.json4s.native.JsonMethods.parse
import spray.can.Http
import spray.http.FormData
import spray.http.MediaTypes
import spray.http.StatusCodes
import spray.httpx.Json4sSupport
import spray.routing._
import spray.routing.authentication.Authentication

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.{Try, Success, Failure}

class  EventServiceActor(
    val eventClient: LEvents,
    val accessKeysClient: AccessKeys,
    val channelsClient: Channels,
    val config: EventServerConfig) extends HttpServiceActor {

  object Json4sProtocol extends Json4sSupport {
    implicit def json4sFormats: Formats = DefaultFormats +
      new EventJson4sSupport.APISerializer +
      new BatchEventsJson4sSupport.APISerializer +
      // NOTE: don't use Json4s JodaTimeSerializers since it has issues,
      // some format not converted, or timezone not correct
      new DateTimeJson4sSupport.Serializer
  }


  val MaxNumberOfEventsPerBatchRequest = 50

  val logger = Logging(context.system, this)

  // we use the enclosing ActorContext's or ActorSystem's dispatcher for our
  // Futures
  implicit def executionContext: ExecutionContext = context.dispatcher

  implicit val timeout = Timeout(5, TimeUnit.SECONDS)

  val rejectionHandler = Common.rejectionHandler

  val jsonPath = """(.+)\.json$""".r
  val formPath = """(.+)\.form$""".r

  val pluginContext = EventServerPluginContext(logger)

  private lazy val base64Decoder = new BASE64Decoder

  case class AuthData(appId: Int, channelId: Option[Int], events: Seq[String])

  /* with accessKey in query/header, return appId if succeed */
  def withAccessKey: RequestContext => Future[Authentication[AuthData]] = {
    ctx: RequestContext =>
      val accessKeyParamOpt = ctx.request.uri.query.get("accessKey")
      val channelParamOpt = ctx.request.uri.query.get("channel")
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
          ctx.request.headers.find(_.name == "Authorization").map { authHeader ⇒
            authHeader.value.split("Basic ") match {
              case Array(_, value) ⇒
                val appAccessKey =
                  new String(base64Decoder.decodeBuffer(value)).trim.split(":")(0)
                accessKeysClient.get(appAccessKey) match {
                  case Some(k) ⇒ Right(AuthData(k.appid, None, k.events))
                  case None ⇒ FailedAuth
                }

              case _ ⇒ FailedAuth
            }
          }.getOrElse(MissedAuth)
        }
      }
  }

  private val FailedAuth = Left(
    AuthenticationFailedRejection(
      AuthenticationFailedRejection.CredentialsRejected, List()
    )
  )

  private val MissedAuth = Left(
    AuthenticationFailedRejection(
      AuthenticationFailedRejection.CredentialsMissing, List()
    )
  )

  lazy val statsActorRef = actorRefFactory.actorSelection("/user/StatsActor")
  lazy val pluginsActorRef = actorRefFactory.actorSelection("/user/PluginsActor")

  val route: Route =
    pathSingleSlash {
      import Json4sProtocol._

      get {
        respondWithMediaType(MediaTypes.`application/json`) {
          complete(Map("status" -> "alive"))
        }
      }
    } ~
    path("plugins.json") {
      import Json4sProtocol._
      get {
        respondWithMediaType(MediaTypes.`application/json`) {
          complete {
            Map("plugins" -> Map(
              "inputblockers" -> pluginContext.inputBlockers.map { case (n, p) =>
                n -> Map(
                  "name" -> p.pluginName,
                  "description" -> p.pluginDescription,
                  "class" -> p.getClass.getName)
              },
              "inputsniffers" -> pluginContext.inputSniffers.map { case (n, p) =>
                n -> Map(
                  "name" -> p.pluginName,
                  "description" -> p.pluginDescription,
                  "class" -> p.getClass.getName)
              }
            ))
          }
        }
      }
    } ~
    path("plugins" / Segments) { segments =>
      get {
        handleExceptions(Common.exceptionHandler) {
          authenticate(withAccessKey) { authData =>
            respondWithMediaType(MediaTypes.`application/json`) {
              complete {
                val pluginArgs = segments.drop(2)
                val pluginType = segments(0)
                val pluginName = segments(1)
                pluginType match {
                  case EventServerPlugin.inputBlocker =>
                    pluginContext.inputBlockers(pluginName).handleREST(
                      authData.appId,
                      authData.channelId,
                      pluginArgs)
                  case EventServerPlugin.inputSniffer =>
                    pluginsActorRef ? PluginsActor.HandleREST(
                      appId = authData.appId,
                      channelId = authData.channelId,
                      pluginName = pluginName,
                      pluginArgs = pluginArgs) map {
                      _.asInstanceOf[String]
                    }
                }
              }
            }
          }
        }
      }
    } ~
    path("events" / jsonPath ) { eventId =>

      import Json4sProtocol._

      get {
        handleExceptions(Common.exceptionHandler) {
          handleRejections(rejectionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              val channelId = authData.channelId
              respondWithMediaType(MediaTypes.`application/json`) {
                complete {
                  logger.debug(s"GET event ${eventId}.")
                  val data = eventClient.futureGet(eventId, appId, channelId).map { eventOpt =>
                    eventOpt.map( event =>
                      (StatusCodes.OK, event)
                    ).getOrElse(
                      (StatusCodes.NotFound, Map("message" -> "Not Found"))
                    )
                  }
                  data
                }
              }
            }
          }
        }
      } ~
      delete {
        handleExceptions(Common.exceptionHandler) {
          handleRejections(rejectionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              val channelId = authData.channelId
              respondWithMediaType(MediaTypes.`application/json`) {
                complete {
                  logger.debug(s"DELETE event ${eventId}.")
                  val data = eventClient.futureDelete(eventId, appId, channelId).map { found =>
                    if (found) {
                      (StatusCodes.OK, Map("message" -> "Found"))
                    } else {
                      (StatusCodes.NotFound, Map("message" -> "Not Found"))
                    }
                  }
                  data
                }
              }
            }
          }
        }
      }
    } ~
    path("events.json") {

      import Json4sProtocol._

      post {
        handleExceptions(Common.exceptionHandler) {
          handleRejections(rejectionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              val channelId = authData.channelId
              val events = authData.events
              entity(as[Event]) { event =>
                complete {
                  if (events.isEmpty || authData.events.contains(event.event)) {
                    pluginContext.inputBlockers.values.foreach(
                      _.process(EventInfo(
                        appId = appId,
                        channelId = channelId,
                        event = event), pluginContext))
                    val data = eventClient.futureInsert(event, appId, channelId).map { id =>
                      pluginsActorRef ! EventInfo(
                        appId = appId,
                        channelId = channelId,
                        event = event)
                      val result = (StatusCodes.Created, Map("eventId" -> s"${id}"))
                      if (config.stats) {
                        statsActorRef ! Bookkeeping(appId, result._1, event)
                      }
                      result
                    }
                    data
                  } else {
                    (StatusCodes.Forbidden,
                      Map("message" -> s"${event.event} events are not allowed"))
                  }
                }
              }
            }
          }
        }
      } ~
      get {
        handleExceptions(Common.exceptionHandler) {
          handleRejections(rejectionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              val channelId = authData.channelId
              parameters(
                'startTime.as[Option[String]],
                'untilTime.as[Option[String]],
                'entityType.as[Option[String]],
                'entityId.as[Option[String]],
                'event.as[Option[String]],
                'targetEntityType.as[Option[String]],
                'targetEntityId.as[Option[String]],
                'limit.as[Option[Int]],
                'reversed.as[Option[Boolean]]) {
                (startTimeStr, untilTimeStr, entityType, entityId,
                  eventName,  // only support one event name
                  targetEntityType, targetEntityId,
                  limit, reversed) =>
                respondWithMediaType(MediaTypes.`application/json`) {
                  complete {
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


                    parseTime.flatMap { case (startTime, untilTime) =>
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
                            (StatusCodes.NotFound,
                              Map("message" -> "Not Found"))
                          }
                        }
                      data
                    }.recover {
                      case e: Exception =>
                        (StatusCodes.BadRequest, Map("message" -> s"${e}"))
                    }
                  }
                }
              }
            }
          }
        }
      }
    } ~
    path("batch" / "events.json") {

      import Json4sProtocol._

      post {
        handleExceptions(Common.exceptionHandler) {
          handleRejections(rejectionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              val channelId = authData.channelId
              val allowedEvents = authData.events
              val handleEvent: PartialFunction[Try[Event], Future[Map[String, Any]]] = {
                case Success(event) => {
                  if (allowedEvents.isEmpty || allowedEvents.contains(event.event)) {
                    pluginContext.inputBlockers.values.foreach(
                      _.process(EventInfo(
                        appId = appId,
                        channelId = channelId,
                        event = event), pluginContext))
                    val data = eventClient.futureInsert(event, appId, channelId).map { id =>
                      pluginsActorRef ! EventInfo(
                        appId = appId,
                        channelId = channelId,
                        event = event)
                      val status = StatusCodes.Created
                      val result = Map(
                        "status" -> status.intValue,
                        "eventId" -> s"${id}")
                      if (config.stats) {
                        statsActorRef ! Bookkeeping(appId, status, event)
                      }
                      result
                    }.recover { case exception =>
                      Map(
                        "status" -> StatusCodes.InternalServerError.intValue,
                        "message" -> s"${exception.getMessage()}")
                    }
                    data
                  } else {
                    Future.successful(Map(
                      "status" -> StatusCodes.Forbidden.intValue,
                      "message" -> s"${event.event} events are not allowed"))
                  }
                }
                case Failure(exception) => {
                  Future.successful(Map(
                    "status" -> StatusCodes.BadRequest.intValue,
                    "message" -> s"${exception.getMessage()}"))
                }
              }

              entity(as[Seq[Try[Event]]]) { events =>
                complete {
                  if (events.length <= MaxNumberOfEventsPerBatchRequest) {
                    Future.traverse(events)(handleEvent)
                  } else {
                    (StatusCodes.BadRequest,
                      Map("message" -> (s"Batch request must have less than or equal to " +
                        s"${MaxNumberOfEventsPerBatchRequest} events")))
                  }
                }
              }
            }
          }
        }
      }
    } ~
    path("stats.json") {

      import Json4sProtocol._

      get {
        handleExceptions(Common.exceptionHandler) {
          handleRejections(rejectionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              respondWithMediaType(MediaTypes.`application/json`) {
                if (config.stats) {
                  complete {
                    statsActorRef ? GetStats(appId) map {
                      _.asInstanceOf[Map[String, StatsSnapshot]]
                    }
                  }
                } else {
                  complete(
                    StatusCodes.NotFound,
                    parse("""{"message": "To see stats, launch Event Server """ +
                      """with --stats argument."}"""))
                }
              }
            }
          }
        }
      }  // stats.json get
    } ~
    path("webhooks" / jsonPath ) { web =>
      import Json4sProtocol._

      post {
        handleExceptions(Common.exceptionHandler) {
          handleRejections(rejectionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              val channelId = authData.channelId
              respondWithMediaType(MediaTypes.`application/json`) {
                entity(as[JObject]) { jObj =>
                  complete {
                    Webhooks.postJson(
                      appId = appId,
                      channelId = channelId,
                      web = web,
                      data = jObj,
                      eventClient = eventClient,
                      log = logger,
                      stats = config.stats,
                      statsActorRef = statsActorRef)
                  }
                }
              }
            }
          }
        }
      } ~
      get {
        handleExceptions(Common.exceptionHandler) {
          handleRejections(rejectionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              val channelId = authData.channelId
              respondWithMediaType(MediaTypes.`application/json`) {
                complete {
                  Webhooks.getJson(
                    appId = appId,
                    channelId = channelId,
                    web = web,
                    log = logger)
                }
              }
            }
          }
        }
      }
    } ~
    path("webhooks" / formPath ) { web =>
      post {
        handleExceptions(Common.exceptionHandler) {
          handleRejections(rejectionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              val channelId = authData.channelId
              respondWithMediaType(MediaTypes.`application/json`) {
                entity(as[FormData]){ formData =>
                  // logger.debug(formData.toString)
                  complete {
                    // respond with JSON
                    import Json4sProtocol._

                    Webhooks.postForm(
                      appId = appId,
                      channelId = channelId,
                      web = web,
                      data = formData,
                      eventClient = eventClient,
                      log = logger,
                      stats = config.stats,
                      statsActorRef = statsActorRef)
                  }
                }
              }
            }
          }
        }
      } ~
      get {
        handleExceptions(Common.exceptionHandler) {
          handleRejections(rejectionHandler) {
            authenticate(withAccessKey) { authData =>
              val appId = authData.appId
              val channelId = authData.channelId
              respondWithMediaType(MediaTypes.`application/json`) {
                complete {
                  // respond with JSON
                  import Json4sProtocol._

                  Webhooks.getForm(
                    appId = appId,
                    channelId = channelId,
                    web = web,
                    log = logger)
                }
              }
            }
          }
        }
      }

    }

  def receive: Actor.Receive = runRoute(route)
}



/* message */
case class StartServer(host: String, port: Int)

class EventServerActor(
    val eventClient: LEvents,
    val accessKeysClient: AccessKeys,
    val channelsClient: Channels,
    val config: EventServerConfig) extends Actor with ActorLogging {
  val child = context.actorOf(
    Props(classOf[EventServiceActor],
      eventClient,
      accessKeysClient,
      channelsClient,
      config),
    "EventServiceActor")
  implicit val system = context.system

  def receive: Actor.Receive = {
    case StartServer(host, portNum) => {
      IO(Http) ! Http.Bind(child, interface = host, port = portNum)
    }
    case m: Http.Bound => log.info("Bound received. EventServer is ready.")
    case m: Http.CommandFailed => log.error("Command failed.")
    case _ => log.error("Unknown message.")
  }
}

case class EventServerConfig(
  ip: String = "localhost",
  port: Int = 7070,
  plugins: String = "plugins",
  stats: Boolean = false)

object EventServer {
  def createEventServer(config: EventServerConfig): Unit = {
    implicit val system = ActorSystem("EventServerSystem")

    val eventClient = Storage.getLEvents()
    val accessKeysClient = Storage.getMetaDataAccessKeys()
    val channelsClient = Storage.getMetaDataChannels()

    val serverActor = system.actorOf(
      Props(
        classOf[EventServerActor],
        eventClient,
        accessKeysClient,
        channelsClient,
        config),
      "EventServerActor"
    )
    if (config.stats) system.actorOf(Props[StatsActor], "StatsActor")
    system.actorOf(Props[PluginsActor], "PluginsActor")
    serverActor ! StartServer(config.ip, config.port)
    system.awaitTermination()
  }
}

object Run {
  def main(args: Array[String]) {
    EventServer.createEventServer(EventServerConfig(
      ip = "0.0.0.0",
      port = 7070))
  }
}
