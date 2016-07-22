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

package org.apache.predictionio.data.api

import java.util.ServiceLoader

import akka.event.LoggingAdapter
import grizzled.slf4j.Logging

import scala.collection.JavaConversions._
import scala.collection.mutable

class EventServerPluginContext(
    val plugins: mutable.Map[String, mutable.Map[String, EventServerPlugin]],
    val log: LoggingAdapter) {
  def inputBlockers: Map[String, EventServerPlugin] =
    plugins.getOrElse(EventServerPlugin.inputBlocker, Map()).toMap

  def inputSniffers: Map[String, EventServerPlugin] =
    plugins.getOrElse(EventServerPlugin.inputSniffer, Map()).toMap
}

object EventServerPluginContext extends Logging {
  def apply(log: LoggingAdapter): EventServerPluginContext = {
    val plugins = mutable.Map[String, mutable.Map[String, EventServerPlugin]](
      EventServerPlugin.inputBlocker -> mutable.Map(),
      EventServerPlugin.inputSniffer -> mutable.Map())
    val serviceLoader = ServiceLoader.load(classOf[EventServerPlugin])
    serviceLoader foreach { service =>
      plugins(service.pluginType) += service.pluginName -> service
    }
    new EventServerPluginContext(
      plugins,
      log)
  }
}
