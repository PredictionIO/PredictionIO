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
package org.apache.predictionio.tools.admin

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.server._
import org.apache.predictionio.data.storage._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import org.apache.predictionio.akkahttpjson4s.Json4sSupport._
import org.json4s.{DefaultFormats, Formats}

object Json4sProtocol {
  implicit val serialization = org.json4s.jackson.Serialization
  implicit def json4sFormats: Formats = DefaultFormats
}

case class AdminServerConfig(
  ip: String = "localhost",
  port: Int = 7071
)

object AdminServer {
  import Json4sProtocol._

  private implicit val timeout: Timeout = Timeout(5, TimeUnit.SECONDS)

  // for better message response
  private val rejectionHandler = RejectionHandler.newBuilder().handle {
    case MalformedRequestContentRejection(msg, _) =>
      complete(StatusCodes.BadRequest, Map("message" -> msg))
    case MissingQueryParamRejection(msg) =>
      complete(StatusCodes.NotFound,
        Map("message" -> s"missing required query parameter ${msg}."))
    case AuthenticationFailedRejection(cause, challengeHeaders) =>
      complete(StatusCodes.Unauthorized, challengeHeaders,
        Map("message" -> s"Invalid accessKey."))
  }.result()

  def createRoute()(implicit executionContext: ExecutionContext): Route = {

    val commandClient = new CommandClient(
      appClient = Storage.getMetaDataApps,
      accessKeyClient = Storage.getMetaDataAccessKeys,
      eventClient = Storage.getLEvents()
    )

    val route =
      pathSingleSlash {
        get {
          complete(Map("status" -> "alive"))
        }
      } ~
      path("cmd" / "app" / Segment / "data") {
        appName => {
          delete {
            complete(commandClient.futureAppDataDelete(appName))
          }
        }
      } ~
      path("cmd" / "app" / Segment) {
        appName => {
          delete {
            complete(commandClient.futureAppDelete(appName))
          }
        }
      } ~
      path("cmd" / "app") {
        get {
          complete(commandClient.futureAppList())
        } ~
        post {
          entity(as[AppRequest]) {
            appArgs =>
              onSuccess(commandClient.futureAppNew(appArgs)){
                case res: GeneralResponse => complete(res)
                case res: AppNewResponse  => complete(res)
              }
          }
        }
      }

    route
  }


  def createAdminServer(config: AdminServerConfig): ActorSystem = {
    implicit val system = ActorSystem("AdminServerSystem")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val route = createRoute()
    Http().bindAndHandle(route, config.ip, config.port)
    system
  }
}

object AdminRun {
  def main (args: Array[String]) : Unit = {
    val f = AdminServer.createAdminServer(AdminServerConfig(
      ip = "localhost",
      port = 7071))
    .whenTerminated

    Await.ready(f, Duration.Inf)
  }
}
