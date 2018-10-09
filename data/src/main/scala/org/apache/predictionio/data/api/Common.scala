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

import akka.http.scaladsl.server._
import org.apache.predictionio.data.storage.StorageException
import org.apache.predictionio.data.webhooks.ConnectorException
import org.json4s.{DefaultFormats, Formats}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import org.apache.predictionio.akkahttpjson4s.Json4sSupport._

object Common {

  object Json4sProtocol {
    implicit val serialization = org.json4s.native.Serialization
    implicit def json4sFormats: Formats = DefaultFormats
  }

  import Json4sProtocol._

  val exceptionHandler = ExceptionHandler {
    case e: ConnectorException => {
      complete(StatusCodes.BadRequest, Map("message" -> s"${e.getMessage()}"))
    }
    case e: StorageException => {
      complete(StatusCodes.InternalServerError, Map("message" -> s"${e.getMessage()}"))
    }
    case e: Exception => {
      complete(StatusCodes.InternalServerError, Map("message" -> s"${e.getMessage()}"))
    }
  }

  val rejectionHandler = RejectionHandler.newBuilder().handle {
    case MalformedRequestContentRejection(msg, _) =>
      complete(StatusCodes.BadRequest, Map("message" -> msg))

    case MissingQueryParamRejection(msg) =>
      complete(StatusCodes.NotFound,
        Map("message" -> s"missing required query parameter ${msg}."))

    case AuthenticationFailedRejection(cause, challengeHeaders) => {
      val msg = cause match {
        case AuthenticationFailedRejection.CredentialsRejected =>
          "Invalid accessKey."
        case AuthenticationFailedRejection.CredentialsMissing =>
          "Missing accessKey."
      }
      complete(StatusCodes.Unauthorized, Map("message" -> msg))
    }
    case ChannelRejection(msg) =>
      complete(StatusCodes.Unauthorized, Map("message" -> msg))
  }.result()
}

/** invalid channel */
case class ChannelRejection(msg: String) extends Rejection
