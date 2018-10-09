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

package org.apache.predictionio.akkahttpjson4s

// Referenced from https://github.com/hseeberger/akka-http-json
// because of the difference of supported json4s version.
import java.lang.reflect.InvocationTargetException

import akka.http.scaladsl.marshalling.{ Marshaller, ToEntityMarshaller }
import akka.http.scaladsl.model.ContentTypeRange
import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
import akka.util.ByteString
import org.json4s.{ Formats, MappingException, Serialization }
import scala.collection.immutable.Seq

/**
  * Automatic to and from JSON marshalling/unmarshalling using an in-scope *Json4s* protocol.
  *
  * Pretty printing is enabled if an implicit [[Json4sSupport.ShouldWritePretty.True]] is in scope.
  */
object Json4sSupport extends Json4sSupport {

  sealed abstract class ShouldWritePretty

  final object ShouldWritePretty {
    final object True  extends ShouldWritePretty
    final object False extends ShouldWritePretty
  }
}

/**
  * Automatic to and from JSON marshalling/unmarshalling using an in-scope *Json4s* protocol.
  *
  * Pretty printing is enabled if an implicit [[Json4sSupport.ShouldWritePretty.True]] is in scope.
  */
trait Json4sSupport {
  import Json4sSupport._

  def unmarshallerContentTypes: Seq[ContentTypeRange] =
    mediaTypes.map(ContentTypeRange.apply)

  def mediaTypes: Seq[MediaType.WithFixedCharset] =
    List(`application/json`)

  private val jsonStringUnmarshaller =
    Unmarshaller.byteStringUnmarshaller
      .forContentTypes(unmarshallerContentTypes: _*)
      .mapWithCharset {
        case (ByteString.empty, _) => throw Unmarshaller.NoContentException
        case (data, charset)       => data.decodeString(charset.nioCharset.name)
      }

  private val jsonStringMarshaller =
    Marshaller.oneOf(mediaTypes: _*)(Marshaller.stringMarshaller)

  /**
    * HTTP entity => `A`
    *
    * @tparam A type to decode
    * @return unmarshaller for `A`
    */
  implicit def unmarshaller[A: Manifest](implicit serialization: Serialization,
    formats: Formats): FromEntityUnmarshaller[A] =
    jsonStringUnmarshaller
      .map(s => serialization.read(s))
      .recover { _ => _ =>
      { case MappingException(_, ite: InvocationTargetException) => throw ite.getCause }
      }

  /**
    * `A` => HTTP entity
    *
    * @tparam A type to encode, must be upper bounded by `AnyRef`
    * @return marshaller for any `A` value
    */
  implicit def marshaller[A <: AnyRef](implicit serialization: Serialization,
    formats: Formats,
    shouldWritePretty: ShouldWritePretty =
    ShouldWritePretty.False): ToEntityMarshaller[A] =
    shouldWritePretty match {
      case ShouldWritePretty.False =>
        jsonStringMarshaller.compose(serialization.write[A])
      case ShouldWritePretty.True =>
        jsonStringMarshaller.compose(serialization.writePretty[A])
    }
}
