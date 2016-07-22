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

package org.apache.predictionio.tools.admin

import akka.actor.{Actor, ActorSystem, Props}
import akka.event.Logging
import akka.io.IO
import akka.util.Timeout
import org.apache.predictionio.data.api.StartServer
import org.apache.predictionio.data.storage.Storage
import org.json4s.{Formats, DefaultFormats}

import java.util.concurrent.TimeUnit

import spray.can.Http
import spray.http.{MediaTypes, StatusCodes}
import spray.httpx.Json4sSupport
import spray.routing._

import scala.concurrent.ExecutionContext

class AdminServiceActor(val commandClient: CommandClient)
  extends HttpServiceActor {

  object Json4sProtocol extends Json4sSupport {
    implicit def json4sFormats: Formats = DefaultFormats
  }

  import Json4sProtocol._

  val log = Logging(context.system, this)

  // we use the enclosing ActorContext's or ActorSystem's dispatcher for our
  // Futures
  implicit def executionContext: ExecutionContext = actorRefFactory.dispatcher
  implicit val timeout: Timeout = Timeout(5, TimeUnit.SECONDS)

  // for better message response
  val rejectionHandler = RejectionHandler {
    case MalformedRequestContentRejection(msg, _) :: _ =>
      complete(StatusCodes.BadRequest, Map("message" -> msg))
    case MissingQueryParamRejection(msg) :: _ =>
      complete(StatusCodes.NotFound,
        Map("message" -> s"missing required query parameter ${msg}."))
    case AuthenticationFailedRejection(cause, challengeHeaders) :: _ =>
      complete(StatusCodes.Unauthorized, challengeHeaders,
        Map("message" -> s"Invalid accessKey."))
  }

  val jsonPath = """(.+)\.json$""".r

  val route: Route =
    pathSingleSlash {
      get {
        respondWithMediaType(MediaTypes.`application/json`) {
          complete(Map("status" -> "alive"))
        }
      }
    } ~
      path("cmd" / "app" / Segment / "data") {
        appName => {
          delete {
            respondWithMediaType(MediaTypes.`application/json`) {
              complete(commandClient.futureAppDataDelete(appName))
            }
          }
        }
      } ~
      path("cmd" / "app" / Segment) {
        appName => {
          delete {
            respondWithMediaType(MediaTypes.`application/json`) {
              complete(commandClient.futureAppDelete(appName))
            }
          }
        }
      } ~
      path("cmd" / "app") {
        get {
          respondWithMediaType(MediaTypes.`application/json`) {
            complete(commandClient.futureAppList())
          }
        } ~
          post {
            entity(as[AppRequest]) {
              appArgs => respondWithMediaType(MediaTypes.`application/json`) {
                complete(commandClient.futureAppNew(appArgs))
              }
            }
          }
      }
  def receive: Actor.Receive = runRoute(route)
}

class AdminServerActor(val commandClient: CommandClient) extends Actor {
  val log = Logging(context.system, this)
  val child = context.actorOf(
    Props(classOf[AdminServiceActor], commandClient),
    "AdminServiceActor")

  implicit val system = context.system

  def receive: PartialFunction[Any, Unit] = {
    case StartServer(host, portNum) => {
      IO(Http) ! Http.Bind(child, interface = host, port = portNum)

    }
    case m: Http.Bound => log.info("Bound received. AdminServer is ready.")
    case m: Http.CommandFailed => log.error("Command failed.")
    case _ => log.error("Unknown message.")
  }
}

case class AdminServerConfig(
  ip: String = "localhost",
  port: Int = 7071
)

object AdminServer {
  def createAdminServer(config: AdminServerConfig): Unit = {
    implicit val system = ActorSystem("AdminServerSystem")

    val commandClient = new CommandClient(
      appClient = Storage.getMetaDataApps,
      accessKeyClient = Storage.getMetaDataAccessKeys,
      eventClient = Storage.getLEvents()
    )

    val serverActor = system.actorOf(
      Props(classOf[AdminServerActor], commandClient),
      "AdminServerActor")
    serverActor ! StartServer(config.ip, config.port)
    system.awaitTermination
  }
}

object AdminRun {
  def main (args: Array[String]) {
    AdminServer.createAdminServer(AdminServerConfig(
      ip = "localhost",
      port = 7071))
  }
}
