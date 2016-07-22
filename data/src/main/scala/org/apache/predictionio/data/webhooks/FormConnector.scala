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

import org.json4s.JObject

/** Connector for Webhooks connection with Form submission data format
  */
private[predictionio] trait FormConnector {

  // TODO: support conversion to multiple events?

  /** Convert from original Form submission data to Event JObject
    * @param data Map of key-value pairs in String type received through webhooks
    * @return Event JObject
   */
  def toEventJson(data: Map[String, String]): JObject

}
