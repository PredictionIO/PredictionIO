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

package org.apache.predictionio.tools.imprt

import org.apache.predictionio.controller.Utils
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.EventJson4sSupport
import org.apache.predictionio.data.storage.Storage
import org.apache.predictionio.tools.Runner
import org.apache.predictionio.workflow.WorkflowContext
import org.apache.predictionio.workflow.WorkflowUtils

import grizzled.slf4j.Logging
import org.json4s.native.Serialization._

import scala.util.{Failure, Try}

case class FileToEventsArgs(
  env: String = "",
  logFile: String = "",
  appId: Int = 0,
  channel: Option[String] = None,
  inputPath: String = "",
  verbose: Boolean = false,
  debug: Boolean = false)

object FileToEvents extends Logging {
  def main(args: Array[String]): Unit = {
    val parser = new scopt.OptionParser[FileToEventsArgs]("FileToEvents") {
      opt[String]("env") action { (x, c) =>
        c.copy(env = x)
      }
      opt[String]("log-file") action { (x, c) =>
        c.copy(logFile = x)
      }
      opt[Int]("appid") action { (x, c) =>
        c.copy(appId = x)
      }
      opt[String]("channel") action { (x, c) =>
        c.copy(channel = Some(x))
      }
      opt[String]("input") action { (x, c) =>
        c.copy(inputPath = x)
      }
      opt[Unit]("verbose") action { (x, c) =>
        c.copy(verbose = true)
      }
      opt[Unit]("debug") action { (x, c) =>
        c.copy(debug = true)
      }
    }
    parser.parse(args, FileToEventsArgs()) map { args =>
      // get channelId
      val channels = Storage.getMetaDataChannels
      val channelMap = channels.getByAppid(args.appId).map(c => (c.name, c.id)).toMap

      val channelId: Option[Int] = args.channel.map { ch =>
        if (!channelMap.contains(ch)) {
          error(s"Channel ${ch} doesn't exist in this app.")
          sys.exit(1)
        }

        channelMap(ch)
      }

      val channelStr = args.channel.map(n => " Channel " + n).getOrElse("")

      WorkflowUtils.modifyLogging(verbose = args.verbose)
      @transient lazy implicit val formats = Utils.json4sDefaultFormats +
        new EventJson4sSupport.APISerializer
      val sc = WorkflowContext(
        mode = "Import",
        batch = "App ID " + args.appId + channelStr,
        executorEnv = Runner.envStringToMap(args.env))
      val rdd = sc.textFile(args.inputPath).filter(_.trim.nonEmpty).map { json =>
        Try(read[Event](json)).recoverWith {
          case e: Throwable =>
            error(s"\nmalformed json => $json")
            Failure(e)
        }.get
      }
      val events = Storage.getPEvents()
      events.write(events = rdd,
        appId = args.appId,
        channelId = channelId)(sc)
      info("Events are imported.")
      info("Done.")
    }
  }
}
