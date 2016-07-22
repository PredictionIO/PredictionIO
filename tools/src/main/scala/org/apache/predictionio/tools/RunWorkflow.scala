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

package org.apache.predictionio.tools

import java.io.File
import java.net.URI

import grizzled.slf4j.Logging
import org.apache.predictionio.data.storage.EngineManifest
import org.apache.predictionio.tools.console.ConsoleArgs
import org.apache.predictionio.workflow.WorkflowUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path

import scala.sys.process._

object RunWorkflow extends Logging {
  def runWorkflow(
      ca: ConsoleArgs,
      core: File,
      em: EngineManifest,
      variantJson: File): Int = {
    // Collect and serialize PIO_* environmental variables
    val pioEnvVars = sys.env.filter(kv => kv._1.startsWith("PIO_")).map(kv =>
      s"${kv._1}=${kv._2}"
    ).mkString(",")

    val sparkHome = ca.common.sparkHome.getOrElse(
      sys.env.getOrElse("SPARK_HOME", "."))

    val hadoopConf = new Configuration
    val hdfs = FileSystem.get(hadoopConf)

    val driverClassPathIndex =
      ca.common.sparkPassThrough.indexOf("--driver-class-path")
    val driverClassPathPrefix =
      if (driverClassPathIndex != -1) {
        Seq(ca.common.sparkPassThrough(driverClassPathIndex + 1))
      } else {
        Seq()
      }
    val extraClasspaths =
      driverClassPathPrefix ++ WorkflowUtils.thirdPartyClasspaths

    val deployModeIndex =
      ca.common.sparkPassThrough.indexOf("--deploy-mode")
    val deployMode = if (deployModeIndex != -1) {
      ca.common.sparkPassThrough(deployModeIndex + 1)
    } else {
      "client"
    }

    val extraFiles = WorkflowUtils.thirdPartyConfFiles

    val mainJar =
      if (ca.build.uberJar) {
        if (deployMode == "cluster") {
          em.files.filter(_.startsWith("hdfs")).head
        } else {
          em.files.filterNot(_.startsWith("hdfs")).head
        }
      } else {
        if (deployMode == "cluster") {
          em.files.filter(_.contains("pio-assembly")).head
        } else {
          core.getCanonicalPath
        }
      }

    val workMode =
      ca.common.evaluation.map(_ => "Evaluation").getOrElse("Training")

    val engineLocation = Seq(
      sys.env("PIO_FS_ENGINESDIR"),
      em.id,
      em.version)

    if (deployMode == "cluster") {
      val dstPath = new Path(engineLocation.mkString(Path.SEPARATOR))
      info("Cluster deploy mode detected. Trying to copy " +
        s"${variantJson.getCanonicalPath} to " +
        s"${hdfs.makeQualified(dstPath).toString}.")
      hdfs.copyFromLocalFile(new Path(variantJson.toURI), dstPath)
    }

    val sparkSubmit =
      Seq(Seq(sparkHome, "bin", "spark-submit").mkString(File.separator)) ++
      ca.common.sparkPassThrough ++
      Seq(
        "--class",
        "org.apache.predictionio.workflow.CreateWorkflow",
        "--name",
        s"PredictionIO $workMode: ${em.id} ${em.version} (${ca.common.batch})") ++
      (if (!ca.build.uberJar) {
        Seq("--jars", em.files.mkString(","))
      } else Seq()) ++
      (if (extraFiles.size > 0) {
        Seq("--files", extraFiles.mkString(","))
      } else {
        Seq()
      }) ++
      (if (extraClasspaths.size > 0) {
        Seq("--driver-class-path", extraClasspaths.mkString(":"))
      } else {
        Seq()
      }) ++
      (if (ca.common.sparkKryo) {
        Seq(
          "--conf",
          "spark.serializer=org.apache.spark.serializer.KryoSerializer")
      } else {
        Seq()
      }) ++
      Seq(
        mainJar,
        "--env",
        pioEnvVars,
        "--engine-id",
        em.id,
        "--engine-version",
        em.version,
        "--engine-variant",
        if (deployMode == "cluster") {
          hdfs.makeQualified(new Path(
            (engineLocation :+ variantJson.getName).mkString(Path.SEPARATOR))).
            toString
        } else {
          variantJson.getCanonicalPath
        },
        "--verbosity",
        ca.common.verbosity.toString) ++
      ca.common.engineFactory.map(
        x => Seq("--engine-factory", x)).getOrElse(Seq()) ++
      ca.common.engineParamsKey.map(
        x => Seq("--engine-params-key", x)).getOrElse(Seq()) ++
      (if (deployMode == "cluster") Seq("--deploy-mode", "cluster") else Seq()) ++
      (if (ca.common.batch != "") Seq("--batch", ca.common.batch) else Seq()) ++
      (if (ca.common.verbose) Seq("--verbose") else Seq()) ++
      (if (ca.common.skipSanityCheck) Seq("--skip-sanity-check") else Seq()) ++
      (if (ca.common.stopAfterRead) Seq("--stop-after-read") else Seq()) ++
      (if (ca.common.stopAfterPrepare) {
        Seq("--stop-after-prepare")
      } else {
        Seq()
      }) ++
      ca.common.evaluation.map(x => Seq("--evaluation-class", x)).
        getOrElse(Seq()) ++
      // If engineParamsGenerator is specified, it overrides the evaluation.
      ca.common.engineParamsGenerator.orElse(ca.common.evaluation)
        .map(x => Seq("--engine-params-generator-class", x))
        .getOrElse(Seq()) ++
      (if (ca.common.batch != "") Seq("--batch", ca.common.batch) else Seq()) ++
      Seq("--json-extractor", ca.common.jsonExtractor.toString)

    info(s"Submission command: ${sparkSubmit.mkString(" ")}")
    Process(sparkSubmit, None, "CLASSPATH" -> "", "SPARK_YARN_USER_ENV" -> pioEnvVars).!
  }

  def newRunWorkflow(ca: ConsoleArgs, em: EngineManifest): Int = {
    val jarFiles = em.files.map(new URI(_))
    val args = Seq(
      "--engine-id",
      em.id,
      "--engine-version",
      em.version,
      "--engine-variant",
      ca.common.variantJson.toURI.toString,
      "--verbosity",
      ca.common.verbosity.toString) ++
      ca.common.engineFactory.map(
        x => Seq("--engine-factory", x)).getOrElse(Seq()) ++
      ca.common.engineParamsKey.map(
        x => Seq("--engine-params-key", x)).getOrElse(Seq()) ++
      (if (ca.common.batch != "") Seq("--batch", ca.common.batch) else Seq()) ++
      (if (ca.common.verbose) Seq("--verbose") else Seq()) ++
      (if (ca.common.skipSanityCheck) Seq("--skip-sanity-check") else Seq()) ++
      (if (ca.common.stopAfterRead) Seq("--stop-after-read") else Seq()) ++
      (if (ca.common.stopAfterPrepare) {
        Seq("--stop-after-prepare")
      } else {
        Seq()
      }) ++
      ca.common.evaluation.map(x => Seq("--evaluation-class", x)).
        getOrElse(Seq()) ++
      // If engineParamsGenerator is specified, it overrides the evaluation.
      ca.common.engineParamsGenerator.orElse(ca.common.evaluation)
        .map(x => Seq("--engine-params-generator-class", x))
        .getOrElse(Seq()) ++
      (if (ca.common.batch != "") Seq("--batch", ca.common.batch) else Seq()) ++
      Seq("--json-extractor", ca.common.jsonExtractor.toString)

    Runner.runOnSpark(
      "org.apache.predictionio.workflow.CreateWorkflow",
      args,
      ca,
      jarFiles)
  }
}
