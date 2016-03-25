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

package io.prediction.authentication

/**
  * This is a (very) simple authentication for the dashboard and engine servers
  * It is highly recommended to implement a stonger authentication mechanism
  */

import com.typesafe.config.ConfigFactory
import spray.http.HttpRequest
import spray.routing.authentication._
import spray.routing.{AuthenticationFailedRejection, RequestContext}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait KeyAuthentication {

  object ServerKey {
    private val config = ConfigFactory.load("server.conf")
    val get = config.getString("io.prediction.server.accessKey")
    val param = "accessKey"
  }

  def withAccessKeyFromFile: RequestContext => Future[Authentication[HttpRequest]] = {
    ctx: RequestContext =>
      val accessKeyParamOpt = ctx.request.uri.query.get(ServerKey.param)
      Future {

        val passedKey = accessKeyParamOpt.getOrElse {
          Left(AuthenticationFailedRejection(
            AuthenticationFailedRejection.CredentialsRejected, List()))
        }

        if (passedKey.equals(ServerKey.get)) Right(ctx.request)
        else Left(AuthenticationFailedRejection(
          AuthenticationFailedRejection.CredentialsRejected, List()))

      }
  }
}
