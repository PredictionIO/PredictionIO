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

package org.apache.predictionio.data.storage

import org.apache.predictionio.annotation.DeveloperApi
import org.apache.predictionio.data.{Utils => DataUtils}
import org.joda.time.DateTime
import org.json4s._

/** :: DeveloperApi ::
  * JSON4S serializer for Joda-Time
  *
  * @group Common
  */
@DeveloperApi
object DateTimeJson4sSupport {

  @transient lazy implicit val formats = DefaultFormats

  /** Serialize DateTime to JValue */
  def serializeToJValue: PartialFunction[Any, JValue] = {
    case d: DateTime => JString(DataUtils.dateTimeToString(d))
  }

  /** Deserialize JValue to DateTime */
  def deserializeFromJValue: PartialFunction[JValue, DateTime] = {
    case jv: JValue => DataUtils.stringToDateTime(jv.extract[String])
  }

  /** Custom JSON4S serializer for Joda-Time */
  class Serializer extends CustomSerializer[DateTime](format => (
    deserializeFromJValue, serializeToJValue))

}
