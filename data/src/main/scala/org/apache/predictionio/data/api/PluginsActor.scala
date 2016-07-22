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

import akka.actor.Actor
import akka.event.Logging

class PluginsActor() extends Actor {
  implicit val system = context.system
  val log = Logging(system, this)

  val pluginContext = EventServerPluginContext(log)

  def receive: PartialFunction[Any, Unit] = {
    case e: EventInfo =>
      pluginContext.inputSniffers.values.foreach(_.process(e, pluginContext))
    case h: PluginsActor.HandleREST =>
      try {
        sender() ! pluginContext.inputSniffers(h.pluginName).handleREST(
          h.appId,
          h.channelId,
          h.pluginArgs)
      } catch {
        case e: Exception =>
          sender() ! s"""{"message":"${e.getMessage}"}"""
      }
    case _ =>
      log.error("Unknown message sent to Event Server input sniffer plugin host.")
  }
}

object PluginsActor {
  case class HandleREST(
    pluginName: String,
    appId: Int,
    channelId: Option[Int],
    pluginArgs: Seq[String])
}

