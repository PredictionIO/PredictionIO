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
import org.apache.predictionio.tools.console.ConsoleArgs
import org.apache.predictionio.workflow.WorkflowUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path

import scala.sys.process._

object Runner extends Logging {
  def envStringToMap(env: String): Map[String, String] =
    env.split(',').flatMap(p =>
      p.split('=') match {
        case Array(k, v) => List(k -> v)
        case _ => Nil
      }
    ).toMap

  def argumentValue(arguments: Seq[String], argumentName: String): Option[String] = {
    val argumentIndex = arguments.indexOf(argumentName)
    try {
      arguments(argumentIndex) // just to make it error out if index is -1
      Some(arguments(argumentIndex + 1))
    } catch {
      case e: IndexOutOfBoundsException => None
    }
  }

  def handleScratchFile(
      fileSystem: Option[FileSystem],
      uri: Option[URI],
      localFile: File): String = {
    val localFilePath = localFile.getCanonicalPath
    (fileSystem, uri) match {
      case (Some(fs), Some(u)) =>
        val dest = fs.makeQualified(Path.mergePaths(
          new Path(u),
          new Path(localFilePath)))
        info(s"Copying $localFile to ${dest.toString}")
        fs.copyFromLocalFile(new Path(localFilePath), dest)
        dest.toUri.toString
      case _ => localFile.toURI.toString
    }
  }

  def cleanup(fs: Option[FileSystem], uri: Option[URI]): Unit = {
    (fs, uri) match {
      case (Some(f), Some(u)) =>
        f.close()
      case _ => Unit
    }
  }

  def detectFilePaths(
      fileSystem: Option[FileSystem],
      uri: Option[URI],
      args: Seq[String]): Seq[String] = {
    args map { arg =>
      val f = try {
        new File(new URI(arg))
      } catch {
        case e: Throwable => new File(arg)
      }
      if (f.exists()) {
        handleScratchFile(fileSystem, uri, f)
      } else {
        arg
      }
    }
  }

  def runOnSpark(
      className: String,
      classArgs: Seq[String],
      ca: ConsoleArgs,
      extraJars: Seq[URI]): Int = {
    // Return error for unsupported cases
    val deployMode =
      argumentValue(ca.common.sparkPassThrough, "--deploy-mode").getOrElse("client")
    val master =
      argumentValue(ca.common.sparkPassThrough, "--master").getOrElse("local")

    (ca.common.scratchUri, deployMode, master) match {
      case (Some(u), "client", m) if m != "yarn-cluster" =>
        error("--scratch-uri cannot be set when deploy mode is client")
        return 1
      case (_, "cluster", m) if m.startsWith("spark://") =>
        error("Using cluster deploy mode with Spark standalone cluster is not supported")
        return 1
      case _ => Unit
    }

    // Initialize HDFS API for scratch URI
    val fs = ca.common.scratchUri map { uri =>
      FileSystem.get(uri, new Configuration())
    }

    // Collect and serialize PIO_* environmental variables
    val pioEnvVars = sys.env.filter(kv => kv._1.startsWith("PIO_")).map(kv =>
      s"${kv._1}=${kv._2}"
    ).mkString(",")

    // Location of Spark
    val sparkHome = ca.common.sparkHome.getOrElse(
      sys.env.getOrElse("SPARK_HOME", "."))

    // Local path to PredictionIO assembly JAR
    val mainJar = handleScratchFile(
      fs,
      ca.common.scratchUri,
      console.Console.coreAssembly(ca.common.pioHome.get))

    // Extra JARs that are needed by the driver
    val driverClassPathPrefix =
      argumentValue(ca.common.sparkPassThrough, "--driver-class-path") map { v =>
        Seq(v)
      } getOrElse {
        Nil
      }

    val extraClasspaths =
      driverClassPathPrefix ++ WorkflowUtils.thirdPartyClasspaths

    // Extra files that are needed to be passed to --files
    val extraFiles = WorkflowUtils.thirdPartyConfFiles map { f =>
      handleScratchFile(fs, ca.common.scratchUri, new File(f))
    }

    val deployedJars = extraJars map { j =>
      handleScratchFile(fs, ca.common.scratchUri, new File(j))
    }

    val sparkSubmitCommand =
      Seq(Seq(sparkHome, "bin", "spark-submit").mkString(File.separator))

    val sparkSubmitJars = if (extraJars.nonEmpty) {
      Seq("--jars", deployedJars.map(_.toString).mkString(","))
    } else {
      Nil
    }

    val sparkSubmitFiles = if (extraFiles.nonEmpty) {
      Seq("--files", extraFiles.mkString(","))
    } else {
      Nil
    }

    val sparkSubmitExtraClasspaths = if (extraClasspaths.nonEmpty) {
      Seq("--driver-class-path", extraClasspaths.mkString(":"))
    } else {
      Nil
    }

    val sparkSubmitKryo = if (ca.common.sparkKryo) {
      Seq(
        "--conf",
        "spark.serializer=org.apache.spark.serializer.KryoSerializer")
    } else {
      Nil
    }

    val verbose = if (ca.common.verbose) Seq("--verbose") else Nil

    val sparkSubmit = Seq(
      sparkSubmitCommand,
      ca.common.sparkPassThrough,
      Seq("--class", className),
      sparkSubmitJars,
      sparkSubmitFiles,
      sparkSubmitExtraClasspaths,
      sparkSubmitKryo,
      Seq(mainJar),
      detectFilePaths(fs, ca.common.scratchUri, classArgs),
      Seq("--env", pioEnvVars),
      verbose).flatten.filter(_ != "")
    info(s"Submission command: ${sparkSubmit.mkString(" ")}")
    val proc = Process(
      sparkSubmit,
      None,
      "CLASSPATH" -> "",
      "SPARK_YARN_USER_ENV" -> pioEnvVars).run()
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      def run(): Unit = {
        cleanup(fs, ca.common.scratchUri)
        proc.destroy()
      }
    }))
    cleanup(fs, ca.common.scratchUri)
    proc.exitValue()
  }
}
