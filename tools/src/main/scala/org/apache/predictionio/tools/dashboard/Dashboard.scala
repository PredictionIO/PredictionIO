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


package org.apache.predictionio.tools.dashboard

import org.apache.predictionio.authentication.KeyAuthentication
import org.apache.predictionio.data.storage.Storage

import scala.concurrent.{Await, ExecutionContext, Future}
import akka.actor.ActorSystem
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import com.github.nscala_time.time.Imports.DateTime
import grizzled.slf4j.Logging
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model.ContentTypes._
import com.typesafe.config.ConfigFactory
import org.apache.predictionio.configuration.SSLConfiguration

import scala.concurrent.duration._

case class DashboardConfig(
  ip: String = "localhost",
  port: Int = 9000)

object Dashboard extends Logging {

  def main(args: Array[String]): Unit = {
    val parser = new scopt.OptionParser[DashboardConfig]("Dashboard") {
      opt[String]("ip") action { (x, c) =>
        c.copy(ip = x)
      } text("IP to bind to (default: localhost).")
      opt[Int]("port") action { (x, c) =>
        c.copy(port = x)
      } text("Port to bind to (default: 9000).")
    }

    parser.parse(args, DashboardConfig()) map { dc =>
      val f = DashboardServer.createDashboard(dc).whenTerminated
      Await.result(f, Duration.Inf)
    }
  }

}

object DashboardServer extends KeyAuthentication with CorsSupport with SSLConfiguration {

  def createDashboard(dc: DashboardConfig): ActorSystem = {
    val systemName = "pio-dashboard"
    implicit val system = ActorSystem(systemName)
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    val serverConfig = ConfigFactory.load("server.conf")
    val sslEnforced = serverConfig.getBoolean("org.apache.predictionio.server.ssl-enforced")
    val route = createRoute(DateTime.now, dc)
    if(sslEnforced){
      val https: HttpsConnectionContext = ConnectionContext.https(sslContext)
      Http().setDefaultServerHttpContext(https)
      Http().bindAndHandle(route, dc.ip, dc.port, connectionContext = https)
    } else {
      Http().bindAndHandle(route, dc.ip, dc.port)
    }
    system
  }

  def createRoute(serverStartTime: DateTime, dc: DashboardConfig)
                 (implicit executionContext: ExecutionContext): Route = {
    val evaluationInstances = Storage.getMetaDataEvaluationInstances
    val pioEnvVars = sys.env.filter(kv => kv._1.startsWith("PIO_"))

    def authenticate[T](authenticator: RequestContext => Future[Either[Rejection, T]]):
        AuthenticationDirective[T] = {
      extractRequestContext.flatMap { requestContext =>
        onSuccess(authenticator(requestContext)).flatMap {
          case Right(x) => provide(x)
          case Left(x)  => reject(x): Directive1[T]
        }
      }
    }

    val route: Route =
      path("") {
        authenticate(withAccessKeyFromFile) { request =>
          get {
            val completedInstances = evaluationInstances.getCompleted
            complete(HttpResponse(entity = HttpEntity(
                `text/html(UTF-8)`,
                 html.index(dc, serverStartTime, pioEnvVars, completedInstances).toString
            )))
          }
        }
      } ~
      pathPrefix("engine_instances" / Segment) { instanceId =>
        path("evaluator_results.txt") {
          get {
            evaluationInstances.get(instanceId).map { i =>
              complete(i.evaluatorResults)
            } getOrElse {
              complete(StatusCodes.NotFound)
            }
          }
        } ~
        path("evaluator_results.html") {
          get {
            evaluationInstances.get(instanceId).map { i =>
              complete(HttpResponse(
                entity = HttpEntity(`text/html(UTF-8)`, i.evaluatorResultsHTML)))
            } getOrElse {
              complete(StatusCodes.NotFound)
            }
          }
        } ~
        path("evaluator_results.json") {
          get {
            evaluationInstances.get(instanceId).map { i =>
              complete(HttpResponse(
                entity = HttpEntity(`application/json`, i.evaluatorResultsJSON)))
            } getOrElse {
              complete(StatusCodes.NotFound)
            }
          }
        } ~
        corsHandler {
          path("local_evaluator_results.json") {
            get {
              evaluationInstances.get(instanceId).map { i =>
                complete(HttpResponse(
                  entity = HttpEntity(`application/json`, i.evaluatorResultsJSON)))
              } getOrElse {
                complete(StatusCodes.NotFound)
              }
            }
          }
        } ~
        pathPrefix("assets") {
          getFromResourceDirectory("assets")
        }
      }

    route
  }

}
