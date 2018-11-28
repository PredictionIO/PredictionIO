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

package org.apache.predictionio.tools.commands

import org.apache.predictionio.core.BuildInfo
import org.apache.predictionio.controller.Utils
import org.apache.predictionio.data.storage
import org.apache.predictionio.tools.EitherLogging
import org.apache.predictionio.tools.{RunWorkflow, RunServer, RunBatchPredict}
import org.apache.predictionio.tools.{
  DeployArgs, WorkflowArgs, SparkArgs, ServerArgs, BatchPredictArgs}
import org.apache.predictionio.tools.console.Console
import org.apache.predictionio.tools.Common._
import org.apache.predictionio.tools.ReturnTypes._
import org.apache.predictionio.workflow.WorkflowUtils

import org.apache.commons.io.FileUtils
import scala.collection.JavaConversions._
import scala.sys.process._
import scalaj.http.Http
import java.io.File

case class BuildArgs(
  sbt: Option[File] = None,
  sbtExtra: Option[String] = None,
  sbtAssemblyPackageDependency: Boolean = true,
  sbtClean: Boolean = false,
  uberJar: Boolean = false,
  forceGeneratePIOSbt: Boolean = false)

case class EngineArgs(
  engineId: Option[String] = None,
  engineVersion: Option[String] = None,
  engineDir: Option[String] = None)

object Engine extends EitherLogging {

  private def detectSbt(sbt: Option[File], pioHome: String): String = {
    sbt map {
      _.getCanonicalPath
    } getOrElse {
      val f = new File(Seq(pioHome, "sbt", "sbt").mkString(File.separator))
      if (f.exists) f.getCanonicalPath else "sbt"
    }
  }

  private def outputSbtError(line: String): Unit = {
    """\[.*error.*\]""".r findFirstIn line foreach { _ => error(line) }
  }

  private def compile(
    buildArgs: BuildArgs,
    pioHome: String,
    engineDirPath: String,
    verbose: Boolean): MaybeError = {

    val f = new File(
      Seq(engineDirPath, "project", "pio-build.sbt").mkString(File.separator))
    if (f.exists || buildArgs.forceGeneratePIOSbt) {
      FileUtils.writeLines(
        new File(engineDirPath, "pio.sbt"),
        Seq(
          "// Generated automatically by pio build.",
          "// Changes in this file will be overridden.",
          "",
          "pioVersion := \"" + BuildInfo.version + "\""))
    }
    implicit val formats = Utils.json4sDefaultFormats

    val sbt = detectSbt(buildArgs.sbt, pioHome)
    info(s"Using command '${sbt}' at ${engineDirPath} to build.")
    info("If the path above is incorrect, this process will fail.")
    val asm =
      if (buildArgs.sbtAssemblyPackageDependency) {
        " assemblyPackageDependency"
      } else {
        ""
      }
    val clean = if (buildArgs.sbtClean) " clean" else ""
    val buildCmd = s"${sbt} ${buildArgs.sbtExtra.getOrElse("")}${clean} " +
      (if (buildArgs.uberJar) "assembly" else s"package${asm}")
    val core = new File(engineDirPath, s"pio-assembly-${BuildInfo.version}.jar")
    if (buildArgs.uberJar) {
      info(s"Uber JAR enabled. Putting ${core.getName} in lib.")
      val dst = new File(engineDirPath, "lib")
      dst.mkdir()
      coreAssembly(pioHome) match {
        case Right(coreFile) =>
          FileUtils.copyFileToDirectory(
            coreFile,
            dst,
            true)
        case Left(errStr) => return Left(errStr)
      }
    } else {
      if (new File(engineDirPath, "engine.json").exists()) {
        info(s"Uber JAR disabled. Making sure lib/${core.getName} is absent.")
        new File("lib", core.getName).delete()
      } else {
        info("Uber JAR disabled, but current working directory does not look " +
          s"like an engine project directory. Please delete lib/${core.getName} manually.")
      }
    }
    info(s"Going to run: ${buildCmd} in ${engineDirPath}")
    try {
      val p = Process(s"${buildCmd}", new File(engineDirPath))
      val r =
        if (verbose) {
          p.!(ProcessLogger(line => info(line), line => error(line)))
        } else {
          p.!(ProcessLogger(
            line => outputSbtError(line),
            line => outputSbtError(line)))
        }
      if (r != 0) {
        logAndFail(s"Return code of build command: ${buildCmd} is ${r}. Aborting.")
      } else {
        logAndSucceed("Compilation finished successfully.")
      }
    } catch {
      case e: java.io.IOException =>
        logAndFail(s"Exception during compilation: ${e.getMessage}")
    }
  }

  def build(
    ea: EngineArgs,
    buildArgs: BuildArgs,
    pioHome: String,
    verbose: Boolean): MaybeError = {

    val engineDirPath = getEngineDirPath(ea.engineDir)
    Template.verifyTemplateMinVersion(
      new File(engineDirPath, "template.json")) match {

      case Left(err) => return Left(err)
      case Right(_) =>
        compile(buildArgs, pioHome, engineDirPath, verbose) match {
          case Left(err) => return Left(err)
          case Right(_) =>
            info("Looking for an engine...")
            val jarFiles = jarFilesForScala(engineDirPath)
            if (jarFiles.isEmpty) {
              return logAndFail("No engine found. Your build might have failed. Aborting.")
            }
            jarFiles foreach { f => info(s"Found ${f.getName}") }
        }
    }
    logAndSucceed("Build finished successfully.")
  }

  /** Training an engine.
    *  The function starts a training process to bu run concurrenlty.
    *
    * @param ea An instance of [[EngineArgs]]
    * @param wa An instance of [[WorkflowArgs]] for running a single training.
    * @param sa An instance of [[SparkArgs]]
    * @param pioHome [[String]] with a path to PIO installation
    * @param verbose A [[Boolean]]
    * @return An instance of [[Expected]] contaning either [[Left]]
    *         with an error message or [[Right]] with a handle to a process
    *         responsible for training and a function () => Unit,
    *         that must be called when the process is complete
    */
  def train(
    ea: EngineArgs,
    wa: WorkflowArgs,
    sa: SparkArgs,
    pioHome: String,
    verbose: Boolean = false): Expected[(Process, () => Unit)] = {

    val engineDirPath = getEngineDirPath(ea.engineDir)
    Template.verifyTemplateMinVersion(
      new File(engineDirPath, "template.json"))
    RunWorkflow.runWorkflow(wa, sa, pioHome, engineDirPath, verbose)
  }

  /** Deploying an engine.
    *  The function starts a new process to be run concerrently.
    *
    * @param ea An instance of [[EngineArgs]]
    * @param engineInstanceId An instance of [[engineInstanceId]]
    * @param serverArgs An instance of [[ServerArgs]]
    * @param sparkArgs An instance of [[SparkArgs]]
    * @param pioHome [[String]] with a path to PIO installation
    * @param verbose A [[Boolean]]
    * @return An instance of [[Expected]] contaning either [[Left]]
    *         with an error message or [[Right]] with a handle to process
    *         of a running angine  and a function () => Unit,
    *         that must be called when the process is complete
    */
  def deploy(
    ea: EngineArgs,
    engineInstanceId: Option[String],
    serverArgs: ServerArgs,
    sparkArgs: SparkArgs,
    pioHome: String,
    verbose: Boolean = false): Expected[(Process, () => Unit)] = {

    val engineDirPath = getEngineDirPath(ea.engineDir)
    val verifyResult = Template.verifyTemplateMinVersion(
      new File(engineDirPath, "template.json"))
    if (verifyResult.isLeft) {
      return Left(verifyResult.left.get)
    }
    val engineInstances = storage.Storage.getMetaDataEngineInstances
    engineInstanceId map { eid =>
      engineInstances.get(eid).map { r =>
        RunServer.runServer(
          r.id, serverArgs, sparkArgs, pioHome, engineDirPath, verbose)
      } getOrElse {
        logAndFail(s"Invalid engine instance ID ${eid}. Aborting.")
      }
    } getOrElse {
      val ei = Console.getEngineInfo(
        serverArgs.variantJson.getOrElse(new File(engineDirPath, "engine.json")),
        engineDirPath)

      engineInstances.getLatestCompleted(
        ei.engineId, ei.engineVersion, ei.variantId).map { r =>
        RunServer.runServer(
          r.id, serverArgs, sparkArgs, pioHome, engineDirPath, verbose)
      } getOrElse {
        logAndFail(s"No valid engine instance found for engine ${ei.engineId} " +
          s"${ei.engineVersion}.\nTry running 'train' before 'deploy'. Aborting.")
      }
    }
  }

  def undeploy(da: DeployArgs): MaybeError = {

    val serverUrl = s"http://${da.ip}:${da.port}"
    info(
      s"Undeploying any existing engine instance at ${serverUrl}")
    try {
      val code = Http(s"${serverUrl}/stop").asString.code
      code match {
        case 200 => Success
        case 404 =>
          logAndFail(s"Another process is using ${serverUrl}. Unable to undeploy.")
        case _ =>
          logAndFail(s"Another process is using ${serverUrl}, or an existing " +
            s"engine server is not responding properly (HTTP ${code}). " +
            "Unable to undeploy.")
      }
    } catch {
      case e: java.net.ConnectException =>
        logAndFail(s"Nothing at ${serverUrl}")
      case _: Throwable =>
        logAndFail("Another process might be occupying " +
          s"${da.ip}:${da.port}. Unable to undeploy.")
    }
  }

  /** Batch predict with an engine.
    *
    * @param ea An instance of [[EngineArgs]]
    * @param engineInstanceId An instance of [[engineInstanceId]]
    * @param batchPredictArgs An instance of [[BatchPredictArgs]]
    * @param sparkArgs An instance of [[SparkArgs]]
    * @param pioHome [[String]] with a path to PIO installation
    * @param verbose A [[Boolean]]
    * @return An instance of [[Expected]] contaning either [[Left]]
    *         with an error message or [[Right]] with a handle to process
    *         of a running angine  and a function () => Unit,
    *         that must be called when the process is complete
    */
  def batchPredict(
    ea: EngineArgs,
    engineInstanceId: Option[String],
    batchPredictArgs: BatchPredictArgs,
    sparkArgs: SparkArgs,
    pioHome: String,
    verbose: Boolean = false): Expected[(Process, () => Unit)] = {

    val engineDirPath = getEngineDirPath(ea.engineDir)
    val verifyResult = Template.verifyTemplateMinVersion(
      new File(engineDirPath, "template.json"))
    if (verifyResult.isLeft) {
      return Left(verifyResult.left.get)
    }
    val ei = Console.getEngineInfo(
      batchPredictArgs.variantJson.getOrElse(new File(engineDirPath, "engine.json")),
      engineDirPath)
    val engineInstances = storage.Storage.getMetaDataEngineInstances
    val engineInstance = engineInstanceId map { eid =>
      engineInstances.get(eid)
    } getOrElse {
      engineInstances.getLatestCompleted(
        ei.engineId, ei.engineVersion, ei.variantId)
    }
    engineInstance map { r =>
      RunBatchPredict.runBatchPredict(
        r.id, batchPredictArgs, sparkArgs, pioHome, engineDirPath, verbose)
    } getOrElse {
      engineInstanceId map { eid =>
        logAndFail(s"Invalid engine instance ID ${eid}. Aborting.")
      } getOrElse {
        logAndFail(s"No valid engine instance found for engine ${ei.engineId} " +
          s"${ei.engineVersion}.\nTry running 'train' before 'batchpredict'. Aborting.")
      }
    }
  }

  /** Running a driver on spark.
    *  The function starts a process and returns immediately
    *
    * @param mainClass A [[String]] with the class containing a main functionto run
    * @param driverArguments Arguments to be passed to the main function
    * @param buildArgs An instance of [[BuildArgs]]
    * @param sparkArgs an instance of [[SparkArgs]]
    * @param pioHome [[String]] with a path to PIO installation
    * @param verbose A [[Boolean]]
    * @return An instance of [[Expected]] contaning either [[Left]]
    *         with an error message or [[Right]] with a handle to a process
    *         of a running driver
    */
  def run(
    ea: EngineArgs,
    mainClass: String,
    driverArguments: Seq[String],
    buildArgs: BuildArgs,
    sparkArgs: SparkArgs,
    pioHome: String,
    verbose: Boolean): Expected[Process] = {

    val engineDirPath = getEngineDirPath(ea.engineDir)

    compile(buildArgs, pioHome, engineDirPath, verbose)

    val extraFiles = WorkflowUtils.thirdPartyConfFiles
    val jarFiles = jarFilesForScala(engineDirPath)
    jarFiles foreach { f => info(s"Found JAR: ${f.getName}") }
    val jarPluginFiles = jarFilesForSpark(pioHome)
    jarPluginFiles foreach { f => info(s"Found JAR: ${f.getName}") }
    val allJarFiles = jarFiles.map(_.getCanonicalPath) ++ jarPluginFiles.map(_.getCanonicalPath)
    val pioLogDir = Option(System.getProperty("pio.log.dir")).getOrElse(s"${pioHome}/log")

    val cmd = s"${getSparkHome(sparkArgs.sparkHome)}/bin/spark-submit --jars " +
      s"${allJarFiles.mkString(",")} " +
      (if (extraFiles.size > 0) {
        s"--files ${extraFiles.mkString(",")} "
      } else {
        ""
      }) +
      "--driver-java-options -Dpio.log.dir=${pioLogDir} " +
      "--class " +
      s"${mainClass} ${sparkArgs.sparkPassThrough.mkString(" ")} " +
      coreAssembly(pioHome) + " " +
      driverArguments.mkString(" ")
    info(s"Submission command: ${cmd}")
    Right(Process(
      cmd,
      None,
      "SPARK_YARN_USER_ENV" -> sys.env.filter(kv => kv._1.startsWith("PIO_")).
        map(kv => s"${kv._1}=${kv._2}").mkString(",")).run())
  }
}
