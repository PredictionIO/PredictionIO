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


package org.apache.predictionio.authentication

/**
  * This is a (very) simple authentication for the dashboard and engine servers
  * It is highly recommended to implement a stonger authentication mechanism
  */

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Rejection, RequestContext}
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait KeyAuthentication {

  object ServerKey {
    private val config = ConfigFactory.load("server.conf")

    val authEnforced = config.getBoolean("org.apache.predictionio.server.key-auth-enforced")
    val get = config.getString("org.apache.predictionio.server.accessKey")

    val param = "accessKey"
  }

  def withAccessKeyFromFile: RequestContext => Future[Either[Rejection, HttpRequest]] = {
    ctx: RequestContext =>
      val accessKeyParamOpt = ctx.request.uri.query().get(ServerKey.param)
      Future {
        val passedKey = accessKeyParamOpt.getOrElse {
          Left(AuthenticationFailedRejection(
            AuthenticationFailedRejection.CredentialsRejected, HttpChallenge("", None)))
        }

        if (!ServerKey.authEnforced || passedKey.equals(ServerKey.get)) Right(ctx.request)
        else Left(AuthenticationFailedRejection(
          AuthenticationFailedRejection.CredentialsRejected, HttpChallenge("", None)))

      }
  }
}
