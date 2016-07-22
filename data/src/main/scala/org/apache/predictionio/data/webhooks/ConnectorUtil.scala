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

import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.EventJson4sSupport

import org.json4s.Formats
import org.json4s.DefaultFormats
import org.json4s.JObject
import org.json4s.native.Serialization.read
import org.json4s.native.Serialization.write


private[predictionio] object ConnectorUtil {

  implicit val eventJson4sFormats: Formats = DefaultFormats +
    new EventJson4sSupport.APISerializer

  // intentionally use EventJson4sSupport.APISerializer to convert
  // from JSON to Event object. Don't allow connector directly create
  // Event object so that the Event object formation is consistent
  // by enforcing JSON format

  def toEvent(connector: JsonConnector, data: JObject): Event = {
    read[Event](write(connector.toEventJson(data)))
  }

  def toEvent(connector: FormConnector, data: Map[String, String]): Event = {
    read[Event](write(connector.toEventJson(data)))
  }

}
