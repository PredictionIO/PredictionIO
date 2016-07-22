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

package org.apache.predictionio.tools.export

import org.apache.predictionio.controller.Utils
import org.apache.predictionio.data.storage.EventJson4sSupport
import org.apache.predictionio.data.storage.Storage
import org.apache.predictionio.tools.Runner
import org.apache.predictionio.workflow.WorkflowContext
import org.apache.predictionio.workflow.WorkflowUtils

import grizzled.slf4j.Logging
import org.apache.spark.sql.SQLContext
import org.json4s.native.Serialization._

case class EventsToFileArgs(
  env: String = "",
  logFile: String = "",
  appId: Int = 0,
  channel: Option[String] = None,
  outputPath: String = "",
  format: String = "parquet",
  verbose: Boolean = false,
  debug: Boolean = false)

object EventsToFile extends Logging {
  def main(args: Array[String]): Unit = {
    val parser = new scopt.OptionParser[EventsToFileArgs]("EventsToFile") {
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
      opt[String]("format") action { (x, c) =>
        c.copy(format = x)
      }
      opt[String]("output") action { (x, c) =>
        c.copy(outputPath = x)
      }
      opt[Unit]("verbose") action { (x, c) =>
        c.copy(verbose = true)
      }
      opt[Unit]("debug") action { (x, c) =>
        c.copy(debug = true)
      }
    }
    parser.parse(args, EventsToFileArgs()) map { args =>
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
        mode = "Export",
        batch = "App ID " + args.appId + channelStr,
        executorEnv = Runner.envStringToMap(args.env))
      val sqlContext = new SQLContext(sc)
      val events = Storage.getPEvents()
      val eventsRdd = events.find(appId = args.appId, channelId = channelId)(sc)
      val jsonStringRdd = eventsRdd.map(write(_))
      if (args.format == "json") {
        jsonStringRdd.saveAsTextFile(args.outputPath)
      } else {
        val jsonRdd = sqlContext.jsonRDD(jsonStringRdd)
        jsonRdd.saveAsParquetFile(args.outputPath)
      }
      info(s"Events are exported to ${args.outputPath}/.")
      info("Done.")
    }
  }
}
