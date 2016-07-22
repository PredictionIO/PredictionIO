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

import java.net.URI
import java.util.ServiceLoader

import akka.event.LoggingAdapter
import com.google.common.io.ByteStreams
import grizzled.slf4j.Logging
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.json4s.DefaultFormats
import org.json4s.Formats
import org.json4s.JObject
import org.json4s.JValue
import org.json4s.native.JsonMethods._

import scala.collection.JavaConversions._
import scala.collection.mutable

class EngineServerPluginContext(
    val plugins: mutable.Map[String, mutable.Map[String, EngineServerPlugin]],
    val pluginParams: mutable.Map[String, JValue],
    val log: LoggingAdapter) {
  def outputBlockers: Map[String, EngineServerPlugin] =
    plugins.getOrElse(EngineServerPlugin.outputBlocker, Map()).toMap
  def outputSniffers: Map[String, EngineServerPlugin] =
    plugins.getOrElse(EngineServerPlugin.outputSniffer, Map()).toMap
}

object EngineServerPluginContext extends Logging {
  implicit val formats: Formats = DefaultFormats

  def apply(log: LoggingAdapter, engineVariant: String): EngineServerPluginContext = {
    val plugins = mutable.Map[String, mutable.Map[String, EngineServerPlugin]](
      EngineServerPlugin.outputBlocker -> mutable.Map(),
      EngineServerPlugin.outputSniffer -> mutable.Map())
    val pluginParams = mutable.Map[String, JValue]()
    val serviceLoader = ServiceLoader.load(classOf[EngineServerPlugin])
    val variantJson = parse(stringFromFile(engineVariant))
    (variantJson \ "plugins").extractOpt[JObject].foreach { pluginDefs =>
      pluginDefs.obj.foreach { pluginParams += _ }
    }
    serviceLoader foreach { service =>
      pluginParams.get(service.pluginName) map { params =>
        if ((params \ "enabled").extractOrElse(false)) {
          info(s"Plugin ${service.pluginName} is enabled.")
          plugins(service.pluginType) += service.pluginName -> service
        } else {
          info(s"Plugin ${service.pluginName} is disabled.")
        }
      } getOrElse {
        info(s"Plugin ${service.pluginName} is disabled.")
      }
    }
    new EngineServerPluginContext(
      plugins,
      pluginParams,
      log)
  }

  private def stringFromFile(filePath: String): String = {
    try {
      val uri = new URI(filePath)
      val fs = FileSystem.get(uri, new Configuration())
      new String(ByteStreams.toByteArray(fs.open(new Path(uri))).map(_.toChar))
    } catch {
      case e: java.io.IOException =>
        error(s"Error reading from file: ${e.getMessage}. Aborting.")
        sys.exit(1)
    }
  }
}
