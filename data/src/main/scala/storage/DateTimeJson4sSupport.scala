/** Copyright 2014 TappingStone, Inc.
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

package io.prediction.data.storage

import io.prediction.data.{ Utils => DataUtils }

import org.json4s._
import org.json4s.native.Serialization.{ read, write }

import org.joda.time.DateTime

object DateTimeJson4sSupport {

  implicit val formats = DefaultFormats

  def serializeToJValue: PartialFunction[Any, JValue] = {
    case d: DateTime => JString(DataUtils.dateTimeToString(d))
  }

  def deserializeFromJValue: PartialFunction[JValue, DateTime] = {
    case jv: JValue => DataUtils.stringToDateTime(jv.extract[String])
  }

  class serializer extends CustomSerializer[DateTime](format => (
    deserializeFromJValue, serializeToJValue))

}
