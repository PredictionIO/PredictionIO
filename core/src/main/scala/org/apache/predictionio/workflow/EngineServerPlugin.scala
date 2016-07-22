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

package org.apache.predictionio.workflow

import org.apache.predictionio.data.storage.EngineInstance
import org.json4s._

trait EngineServerPlugin {
  val pluginName: String
  val pluginDescription: String
  val pluginType: String

  def start(context: EngineServerPluginContext): Unit

  def process(
    engineInstance: EngineInstance,
    query: JValue,
    prediction: JValue,
    context: EngineServerPluginContext): JValue

  def handleREST(arguments: Seq[String]): String
}

object EngineServerPlugin {
  val outputBlocker = "outputblocker"
  val outputSniffer = "outputsniffer"
}
