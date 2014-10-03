/** Copyright 2014 TappingStone, Inc.
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

package io.prediction.tools

import io.prediction.data.storage.EngineManifest

import grizzled.slf4j.Logging

import scala.sys.process._

import java.io.File

object RunServer extends Logging {
  def runServer(
      ca: ConsoleArgs,
      core: File,
      em: EngineManifest,
      engineInstanceId: String): Unit = {
    val pioEnvVars = sys.env.filter(kv => kv._1.startsWith("PIO_")).map(kv =>
      s"${kv._1}=${kv._2}"
    ).mkString(",")

    val sparkHome = ca.common.sparkHome.getOrElse(
      sys.env.get("SPARK_HOME").getOrElse("."))

    val sparkSubmit =
      Seq(Seq(sparkHome, "bin", "spark-submit").mkString(File.separator)) ++
      ca.common.sparkPassThrough ++
      Seq(
        "--class",
        "io.prediction.workflow.CreateServer",
        "--name",
        s"PredictionIO Engine Instance: ${engineInstanceId}",
        "--jars",
        em.files.mkString(","),
        core.getCanonicalPath,
        "--engineInstanceId",
        engineInstanceId,
        "--ip",
        ca.ip,
        "--port",
        ca.port.toString)

    info(s"Submission command: ${sparkSubmit.mkString(" ")}")

    val proc =
      Process(sparkSubmit, None, "SPARK_YARN_USER_ENV" -> pioEnvVars).run
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      def run(): Unit = {
        proc.destroy
      }
    }))
    proc.exitValue
  }
}
