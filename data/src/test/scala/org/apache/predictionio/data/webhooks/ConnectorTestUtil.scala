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

package org.apache.predictionio.data.webhooks

import org.specs2.execute.Result
import org.specs2.mutable._

import org.json4s.JObject
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods.parse
import org.json4s.native.Serialization.write

/** TestUtil for JsonConnector */
trait ConnectorTestUtil extends Specification {

  implicit val formats = DefaultFormats

  def check(connector: JsonConnector, original: String, event: String): Result = {
    val originalJson = parse(original).asInstanceOf[JObject]
    val eventJson = parse(event).asInstanceOf[JObject]
    // write and parse back to discard any JNothing field
    val result = parse(write(connector.toEventJson(originalJson))).asInstanceOf[JObject]
    result.obj must containTheSameElementsAs(eventJson.obj)
  }

  def check(connector: FormConnector, original: Map[String, String], event: String) = {

    val eventJson = parse(event).asInstanceOf[JObject]
    // write and parse back to discard any JNothing field
    val result = parse(write(connector.toEventJson(original))).asInstanceOf[JObject]

    result.obj must containTheSameElementsAs(eventJson.obj)
  }
}
