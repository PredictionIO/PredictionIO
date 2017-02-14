/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.predictionio.tools.ReturnTypes._
import org.apache.predictionio.workflow.WorkflowUtils

import scala.sys.process._

case class SparkArgs(
  sparkHome: Option[String] = None,
  sparkPassThrough: Seq[String] = Seq(),
  sparkKryo: Boolean = false,
  scratchUri: Option[URI] = None)

object Runner extends EitherLogging {
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
      case _ => ()
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
      sa: SparkArgs,
      extraJars: Seq[URI],
      pioHome: String,
      verbose: Boolean = false): Expected[(Process, () => Unit)] = {
    // Return error for unsupported cases
    val deployMode =
      argumentValue(sa.sparkPassThrough, "--deploy-mode").getOrElse("client")
    val master =
      argumentValue(sa.sparkPassThrough, "--master").getOrElse("local")

    (sa.scratchUri, deployMode, master) match {
      case (Some(u), "client", m) if m != "yarn-cluster" =>
        return logAndFail("--scratch-uri cannot be set when deploy mode is client")
      case (_, "cluster", m) if m.startsWith("spark://") =>
        return logAndFail(
          "Using cluster deploy mode with Spark standalone cluster is not supported")
      case _ => ()
    }

    // Initialize HDFS API for scratch URI
    val fs = sa.scratchUri map { uri =>
      FileSystem.get(uri, new Configuration())
    }

    // Collect and serialize PIO_* environmental variables
    val pioEnvVars = sys.env.filter(kv => kv._1.startsWith("PIO_")).map(kv =>
      s"${kv._1}=${kv._2}"
    ).mkString(",")

    // Location of Spark
    val sparkHome = sa.sparkHome.getOrElse(
      sys.env.getOrElse("SPARK_HOME", "."))

    // Local path to PredictionIO assembly JAR
    val mainJar = Common.coreAssembly(pioHome) fold(
        errStr => return Left(errStr),
        assembly => handleScratchFile(fs, sa.scratchUri, assembly)
      )

    // Extra JARs that are needed by the driver
    val driverClassPathPrefix =
      argumentValue(sa.sparkPassThrough, "--driver-class-path") map { v =>
        Seq(v)
      } getOrElse {
        Nil
      }

    val extraClasspaths =
      driverClassPathPrefix ++ WorkflowUtils.thirdPartyClasspaths

    // Extra files that are needed to be passed to --files
    val extraFiles = WorkflowUtils.thirdPartyConfFiles map { f =>
      handleScratchFile(fs, sa.scratchUri, new File(f))
    }

    val deployedJars = extraJars map { j =>
      handleScratchFile(fs, sa.scratchUri, new File(j))
    }

    val sparkSubmitCommand =
      Seq(Seq(sparkHome, "bin", "spark-submit").mkString(File.separator))

    val sparkSubmitJarsList = WorkflowUtils.thirdPartyJars ++ deployedJars
    val sparkSubmitJars = if (sparkSubmitJarsList.nonEmpty) {
      Seq("--jars", sparkSubmitJarsList.map(_.toString).mkString(","))
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

    val sparkSubmitKryo = if (sa.sparkKryo) {
      Seq(
        "--conf",
        "spark.serializer=org.apache.spark.serializer.KryoSerializer")
    } else {
      Nil
    }

    val verboseArg = if (verbose) Seq("--verbose") else Nil

    val sparkSubmit = Seq(
      sparkSubmitCommand,
      sa.sparkPassThrough,
      Seq("--class", className),
      sparkSubmitJars,
      sparkSubmitFiles,
      sparkSubmitExtraClasspaths,
      sparkSubmitKryo,
      Seq(mainJar),
      detectFilePaths(fs, sa.scratchUri, classArgs),
      Seq("--env", pioEnvVars),
      verboseArg).flatten.filter(_ != "")
    info(s"Submission command: ${sparkSubmit.mkString(" ")}")
    val proc = Process(
      sparkSubmit,
      None,
      "CLASSPATH" -> "",
      "SPARK_YARN_USER_ENV" -> pioEnvVars).run()
    Right((proc, () => cleanup(fs, sa.scratchUri)))
  }
}
