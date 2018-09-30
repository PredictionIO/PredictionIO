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

import akka.http.scaladsl.server.Route
import org.apache.predictionio.data.storage.{AccessKey, Storage}

import scala.concurrent.ExecutionContext

case class AdminServerConfig(
  ip: String = "localhost",
  port: Int = 7071
)

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import scala.util.{Success, Failure}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._

object AdminServer {

  def createRoute()(implicit executionContext: ExecutionContext): Route = {
    implicit val generalResponseProtocol = jsonFormat2(GeneralResponse)
    implicit val appRequestProtocol      = jsonFormat3(AppRequest)
    implicit val accessKeyProtocol       = jsonFormat3(AccessKey)
    implicit val appResponseProtocol     = jsonFormat3(AppResponse)
    implicit val appListResponseProtocol = jsonFormat3(AppListResponse)
    implicit val appNewResponseProtocol  = jsonFormat5(AppNewResponse)

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
            onComplete(commandClient.futureAppDataDelete(appName)){
              case Success(res) => complete(res)
              case Failure(ex) =>
                ex.printStackTrace()
                complete(StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}")
            }
          }
        }
      } ~
      path("cmd" / "app" / Segment) {
        appName => {
          delete {
            onComplete(commandClient.futureAppDelete(appName)){
              case Success(res) => complete(res)
              case Failure(ex) =>
                ex.printStackTrace()
                complete(StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}")
            }
          }
        }
      } ~
      path("cmd" / "app") {
        get {
          onComplete(commandClient.futureAppList()){
            case Success(res) => complete(res)
            case Failure(ex) =>
              ex.printStackTrace()
              complete(StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}")
          }
        } ~
        post {
          entity(as[AppRequest]) {
            appArgs =>
              onComplete(commandClient.futureAppNew(appArgs)){
                case Success(res) => res match {
                  case res: GeneralResponse => complete(res)
                  case res: AppNewResponse  => complete(res)
                }
                case Failure(ex) =>
                  ex.printStackTrace()
                  complete(StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}")
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
    .whenTerminated.wait()
  }
}
