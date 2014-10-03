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

import io.prediction.controller.Utils
import io.prediction.core.BuildInfo
import io.prediction.data.storage.EngineManifest
import io.prediction.data.storage.EngineManifestSerializer
import io.prediction.data.storage.Storage
import io.prediction.tools.dashboard.Dashboard
import io.prediction.tools.dashboard.DashboardConfig
import io.prediction.data.api.EventServer
import io.prediction.data.api.EventServerConfig

import grizzled.slf4j.Logging
import org.apache.commons.io.FileUtils
import org.json4s._
import org.json4s.native.Serialization.{read, write}
import scalaj.http.Http

import scala.io.Source
import scala.sys.process._

import java.io.File
import java.nio.file.Files

case class ConsoleArgs(
  common: CommonArgs = CommonArgs(),
  build: BuildArgs = BuildArgs(),
  commands: Seq[String] = Seq(),
  batch: String = "Transient Lazy Val",
  metricsClass: Option[String] = None,
  dataSourceParamsJsonPath: Option[String] = None,
  preparatorParamsJsonPath: Option[String] = None,
  algorithmsParamsJsonPath: Option[String] = None,
  servingParamsJsonPath: Option[String] = None,
  metricsParamsJsonPath: Option[String] = None,
  paramsPath: String = "params",
  engineInstanceId: Option[String] = None,
  ip: String = "localhost",
  port: Int = 8000,
  mainClass: Option[String] = None,
  projectName: Option[String] = None,
  directoryName: Option[String] = None)

case class CommonArgs(
  sparkPassThrough: Seq[String] = Seq(),
  driverPassThrough: Seq[String] = Seq(),
  pioHome: Option[String] = None,
  sparkHome: Option[String] = None,
  engineJson: File = new File("engine.json"))

case class BuildArgs(
  sbt: Option[File] = None,
  sbtExtra: Option[String] = None,
  sbtAssemblyPackageDependency: Boolean = false,
  sbtClean: Boolean = false)

object Console extends Logging {
  val distFilename = "DIST"
  def main(args: Array[String]): Unit = {
    val parser = new scopt.OptionParser[ConsoleArgs]("pio") {
      override def showUsageOnError = false
      head("PredictionIO Command Line Interface Console", BuildInfo.version)
      help("help")
      note("Note that it is possible to supply pass-through arguments at\n" +
        "the end of the command by using a '--' separator, e.g.\n\n" +
        "pio train --params-path params -- --master spark://mycluster:7077\n" +
        "\nIn the example above, the '--master' argument will be passed to\n" +
        "underlying spark-submit command. Please refer to the usage section\n" +
        "for each command for more information.\n\n" +
        "The following options are common to all commands:\n")
      opt[String]("pio-home") action { (x, c) =>
        c.copy(common = c.common.copy(pioHome = Some(x)))
      } text("Root directory of a PredictionIO installation.\n" +
        "        Specify this if automatic discovery fail.")
      opt[String]("spark-home") action { (x, c) =>
        c.copy(common = c.common.copy(sparkHome = Some(x)))
      } text("Root directory of an Apache Spark installation.\n" +
        "        If not specified, will try to use the SPARK_HOME\n" +
        "        environmental variable. If this fails as well, default to\n" +
        "        current directory.")
      opt[File]("engine-json") action { (x, c) =>
        c.copy(common = c.common.copy(engineJson = x))
      } validate { x =>
        if (x.exists)
          success
        else
          failure(s"${x.getCanonicalPath} does not exist.")
      } text("Path to an engine JSON file. Default: engine.json")
      opt[File]("sbt") action { (x, c) =>
        c.copy(build = c.build.copy(sbt = Some(x)))
      } validate { x =>
        if (x.exists)
          success
        else
          failure(s"${x.getCanonicalPath} does not exist.")
      } text("Path to sbt. Default: sbt")
      note("")
      cmd("new").
        text("Creates a new engine project in a subdirectory with the same " +
          "name as the project. The project name will also be used as the " +
          "default engine ID.").
        action { (_, c) =>
          c.copy(commands = c.commands :+ "new")
        } children(
          arg[String]("<project name>") action { (x, c) =>
            c.copy(projectName = Some(x))
          } text("Engine project name.")
        )
      note("")
      cmd("instance").
        text("Creates a new engine instance in a subdirectory with the same " +
          "name as the engine's ID by default.").
        action { (_, c) =>
          c.copy(commands = c.commands :+ "instance")
        } children(
          arg[String]("<engine ID>") action { (x, c) =>
            c.copy(projectName = Some(x))
          } text("Engine ID."),
          opt[String]("directory-name") action { (x, c) =>
            c.copy(directoryName = Some(x))
          } text("Engine instance directory name.")
        )
      note("")
      cmd("register").
        text("Build and register an engine at the current directory.\n" +
          "If the engine at the current directory is a PredictionIO\n" +
          "built-in engine that is not part of PredictionIO's source tree,\n" +
          "the build step will be skipped.").
        action { (_, c) =>
          c.copy(commands = c.commands :+ "register")
        } children(
          opt[String]("sbt-extra") action { (x, c) =>
            c.copy(build = c.build.copy(sbtExtra = Some(x)))
          } text("Extra command to pass to SBT when it builds your engine."),
          opt[Unit]("clean") action { (x, c) =>
            c.copy(build = c.build.copy(sbtClean = true))
          } text("Clean build."),
          opt[Unit]("asm") action { (x, c) =>
            c.copy(build = c.build.copy(sbtAssemblyPackageDependency = true))
          } text("Build dependencies assembly.")
        )
      note("")
      cmd("unregister").
        text("Unregister an engine at the current directory.").
        action { (_, c) =>
          c.copy(commands = c.commands :+ "unregister")
        }
      note("")
      cmd("train").
        text("Kick off a training using an engine. This will produce an\n" +
          "engine instance. This command will pass all pass-through\n" +
          "arguments to its underlying spark-submit command.").
        action { (_, c) =>
          c.copy(commands = c.commands :+ "train")
        } children(
          opt[String]("batch") action { (x, c) =>
            c.copy(batch = x)
          } text("Batch label of the run."),
          opt[String]("params-path") action { (x, c) =>
            c.copy(paramsPath = x)
          } text("Directory to lookup parameters JSON files. Default: params"),
          opt[String]("datasource-params") abbr("dsp") action { (x, c) =>
            c.copy(dataSourceParamsJsonPath = Some(x))
          } text("Data source parameters JSON file. Will try to use\n" +
            "        datasource.json in the base path."),
          opt[String]("preparator-params") abbr("pp") action { (x, c) =>
            c.copy(preparatorParamsJsonPath = Some(x))
          } text("Preparator parameters JSON file. Will try to use\n" +
            "        preparator.json in the base path."),
          opt[String]("algorithms-params") abbr("ap") action { (x, c) =>
            c.copy(algorithmsParamsJsonPath = Some(x))
          } text("Algorithms parameters JSON file. Will try to use\n" +
            "        algorithms.json in the base path."),
          opt[String]("serving-params") abbr("sp") action { (x, c) =>
            c.copy(servingParamsJsonPath = Some(x))
          } text("Serving parameters JSON file. Will try to use\n" +
            "        serving.json in the base path."),
          opt[String]("metrics-params") abbr("mp") action { (x, c) =>
            c.copy(metricsParamsJsonPath = Some(x))
          } text("Metrics parameters JSON file. Will try to use\n" +
            "        metrics.json in the base path.")
        )
      note("")
      cmd("eval").
        text("Kick off an evaluation using an engine. This will produce an\n" +
          "engine instance. This command will pass all pass-through\n" +
          "arguments to its underlying spark-submit command.").
        action { (_, c) =>
          c.copy(commands = c.commands :+ "eval")
        } children(
          opt[String]("batch") action { (x, c) =>
            c.copy(batch = x)
          } text("Batch label of the run."),
          opt[String]("params-path") action { (x, c) =>
            c.copy(paramsPath = x)
          } text("Directory to lookup parameters JSON files. Default: params"),
          opt[String]("metrics-class") required() action { (x, c) =>
            c.copy(metricsClass = Some(x))
          } text("Name of metrics class to run."),
          opt[String]("datasource-params") abbr("dsp") action { (x, c) =>
            c.copy(dataSourceParamsJsonPath = Some(x))
          } text("Data source parameters JSON file. Will try to use\n" +
            "        datasource.json in the base path."),
          opt[String]("preparator-params") abbr("pp") action { (x, c) =>
            c.copy(preparatorParamsJsonPath = Some(x))
          } text("Preparator parameters JSON file. Will try to use\n" +
            "        preparator.json in the base path."),
          opt[String]("algorithms-params") abbr("ap") action { (x, c) =>
            c.copy(algorithmsParamsJsonPath = Some(x))
          } text("Algorithms parameters JSON file. Will try to use\n" +
            "        algorithms.json in the base path."),
          opt[String]("serving-params") abbr("sp") action { (x, c) =>
            c.copy(servingParamsJsonPath = Some(x))
          } text("Serving parameters JSON file. Will try to use\n" +
            "        serving.json in the base path."),
          opt[String]("metrics-params") abbr("mp") action { (x, c) =>
            c.copy(metricsParamsJsonPath = Some(x))
          } text("Metrics parameters JSON file. Will try to use\n" +
            "        metrics.json in the base path.")
        )
      note("")
      cmd("deploy").
        text("Deploy an engine instance as a prediction server. This\n" +
          "command will pass all pass-through arguments to its underlying\n" +
          "spark-submit command.").
        action { (_, c) =>
          c.copy(commands = c.commands :+ "deploy")
        } children(
          opt[String]("engine-instance-id") action { (x, c) =>
            c.copy(engineInstanceId = Some(x))
          } text("Engine instance ID."),
          opt[String]("ip") action { (x, c) =>
            c.copy(ip = x)
          } text("IP to bind to. Default: localhost"),
          opt[Int]("port") action { (x, c) =>
            c.copy(port = x)
          } text("Port to bind to. Default: 8000")
        )
      note("")
      cmd("undeploy").
        text("Undeploy an engine instance as a prediction server.").
        action { (_, c) =>
          c.copy(commands = c.commands :+ "undeploy")
        } children(
          opt[String]("ip") action { (x, c) =>
            c.copy(ip = x)
          } text("IP to unbind from. Default: localhost"),
          opt[Int]("port") action { (x, c) =>
            c.copy(port = x)
          } text("Port to unbind from. Default: 8000")
        )
      note("")
      cmd("dashboard").
        text("Launch a dashboard at the specific IP and port.").
        action { (_, c) =>
          c.copy(
            commands = c.commands :+ "dashboard",
            port = 9000)
        } children(
          opt[String]("ip") action { (x, c) =>
            c.copy(ip = x)
          } text("IP to bind to. Default: localhost"),
          opt[Int]("port") action { (x, c) =>
            c.copy(port = x)
          } text("Port to bind to. Default: 9000")
        )
      note("")
      cmd("eventserver").
        text("Launch an Event Server at the specific IP and port.").
        action { (_, c) =>
          c.copy(
            commands = c.commands :+ "eventserver",
            port = 7070)
        } children(
          opt[String]("ip") action { (x, c) =>
            c.copy(ip = x)
          } text("IP to bind to. Default: localhost"),
          opt[Int]("port") action { (x, c) =>
            c.copy(port = x)
          } text("Port to bind to. Default: 7070")
        )
      note("")
      cmd("compile").
        text("Compile a driver program.").
        action { (_, c) =>
          c.copy(commands = c.commands :+ "compile")
        } children(
          opt[String]("sbt-extra") action { (x, c) =>
            c.copy(build = c.build.copy(sbtExtra = Some(x)))
          } text("Extra command to pass to SBT when it builds your engine."),
          opt[Unit]("clean") action { (x, c) =>
            c.copy(build = c.build.copy(sbtClean = true))
          } text("Clean build."),
          opt[Unit]("asm") action { (x, c) =>
            c.copy(build = c.build.copy(sbtAssemblyPackageDependency = true))
          } text("Build dependencies assembly.")
        )
      note("")
      cmd("run").
        text("Launch a driver program. This command will pass all\n" +
          "pass-through arguments to its underlying spark-submit command.\n" +
          "In addition, it also supports a second level of pass-through\n" +
          "arguments to the driver program, e.g.\n" +
          "pio run -- --master spark://localhost:7077 -- --driver-arg foo").
        action { (_, c) =>
          c.copy(commands = c.commands :+ "run")
        } children(
          arg[String]("<main class>") action { (x, c) =>
            c.copy(mainClass = Some(x))
          } text("Main class name of the driver program."),
          opt[String]("sbt-extra") action { (x, c) =>
            c.copy(build = c.build.copy(sbtExtra = Some(x)))
          } text("Extra command to pass to SBT when it builds your engine."),
          opt[Unit]("clean") action { (x, c) =>
            c.copy(build = c.build.copy(sbtClean = true))
          } text("Clean build."),
          opt[Unit]("asm") action { (x, c) =>
            c.copy(build = c.build.copy(sbtAssemblyPackageDependency = true))
          } text("Build dependencies assembly.")
        )
      note("")
      cmd("dist").
        text("Build an engine at the current directory and create a \n" +
          "distributable package.\n" +
          "If the engine at the current directory is a PredictionIO\n" +
          "built-in engine that is not part of PredictionIO's source tree,\n" +
          "the build step will be skipped.").
        action { (_, c) =>
          c.copy(commands = c.commands :+ "dist")
        } children(
          opt[String]("sbt-extra") action { (x, c) =>
            c.copy(build = c.build.copy(sbtExtra = Some(x)))
          } text("Extra command to pass to SBT when it builds your engine."),
          opt[Unit]("clean") action { (x, c) =>
            c.copy(build = c.build.copy(sbtClean = true))
          } text("Clean build."),
          opt[Unit]("asm") action { (x, c) =>
            c.copy(build = c.build.copy(sbtAssemblyPackageDependency = true))
          } text("Build dependencies assembly.")
        )
    }

    val separatorIndex = args.indexWhere(_ == "--")
    val (consoleArgs, theRest) =
      if (separatorIndex == -1)
        (args, Array[String]())
      else
        args.splitAt(separatorIndex)
    val allPassThroughArgs = theRest.drop(1)
    val secondSepIdx = allPassThroughArgs.indexWhere(_ == "--")
    val (sparkPassThroughArgs, driverPassThroughArgs) =
      if (secondSepIdx == -1)
        (allPassThroughArgs, Array[String]())
      else {
        val t = allPassThroughArgs.splitAt(secondSepIdx)
        (t._1, t._2.drop(1))
      }

    parser.parse(consoleArgs, ConsoleArgs()) map { pca =>
      val ca = pca.copy(common = pca.common.copy(
        sparkPassThrough = sparkPassThroughArgs,
        driverPassThrough = driverPassThroughArgs))
      ca.commands match {
        case Seq("new") =>
          createProject(ca)
        case Seq("instance") =>
          createInstance(ca)
        case Seq("register") =>
          register(ca)
        case Seq("unregister") =>
          unregister(ca)
        case Seq("train") =>
          train(ca)
        case Seq("eval") =>
          train(ca)
        case Seq("deploy") =>
          deploy(ca)
        case Seq("undeploy") =>
          undeploy(ca)
        case Seq("dashboard") =>
          dashboard(ca)
        case Seq("eventserver") =>
          eventserver(ca)
        case Seq("compile") =>
          compile(ca)
        case Seq("run") =>
          run(ca)
        case Seq("dist") =>
          dist(ca)
        case _ =>
          error(
            s"Unrecognized command sequence: ${ca.commands.mkString(" ")}\n")
          System.err.println(parser.usage)
          sys.exit(1)
      }
    }
    sys.exit(0)
  }

  def createProject(ca: ConsoleArgs): Unit = {
    val scalaEngineTemplate = Map(
      "build.sbt" -> templates.scala.txt.buildSbt(
        ca.projectName.get,
        BuildInfo.version,
        BuildInfo.sparkVersion),
      "engine.json" -> templates.scala.txt.engineJson(
        ca.projectName.get,
        "0.0.1-SNAPSHOT",
        ca.projectName.get,
        "myorg.MyEngineFactory"),
      joinFile(Seq("params", "datasource.json")) ->
        templates.scala.params.txt.datasourceJson(),
      joinFile(Seq("project", "assembly.sbt")) ->
        templates.scala.project.txt.assemblySbt(),
      joinFile(Seq("src", "main", "scala", "Engine.scala")) ->
        templates.scala.src.main.scala.txt.engine())

    val template = ca.projectName.get match {
      case _ =>
        info(s"Creating Scala engine project ${ca.projectName.get}")
        scalaEngineTemplate
    }

    writeTemplate(template, ca.projectName.get)

    info(s"Engine project created in subdirectory ${ca.projectName.get}.")
  }

  def createInstance(ca: ConsoleArgs): Unit = {
    val targetDir = ca.directoryName.getOrElse(ca.projectName.get)
    val engineId = ca.projectName.getOrElse("")

    val templateOpt = BuiltInEngine.idInstanceMap.get(engineId)

    if (templateOpt.isEmpty) {
      val engineIdList = BuiltInEngine.instances
        .zipWithIndex
        .map { case(eit, idx) => s"  ${eit.engineId}" }
        .mkString("\n")
      error(s"${engineId} is not a built-in engine. \n" +
        s"Below are built-in engines: \n$engineIdList\n" +
        s"Aborting.")
      sys.exit(1)
    }

    writeTemplate(templateOpt.get.template, targetDir)

    info(s"Engine instance created in subdirectory ${targetDir}.")
  }

  private def writeTemplate(template: Map[String, Any], targetDir: String) = {
    try {
      template map { ft =>
        FileUtils.writeStringToFile(
          new File(targetDir, ft._1),
          ft._2.toString,
          "ISO-8859-1")
      }
    } catch {
      case e: java.io.IOException =>
        error(s"Error occurred while generating template: ${e.getMessage}")
        error("Aborting.")
        sys.exit(1)
    }
  }

  def register(ca: ConsoleArgs): Unit = {
    if (builtinEngineDir ||
      !RegisterEngine.builtinEngine(ca.common.engineJson)) {
      if (!distEngineDir) compile(ca)
      info("Locating files to be registered.")
      val jarFiles = jarFilesForScala
      if (jarFiles.size == 0) {
        error("No files can be found for registration. Aborting.")
        sys.exit(1)
      }
      jarFiles foreach { f => info(s"Found ${f.getName}")}
      RegisterEngine.registerEngine(ca.common.engineJson, jarFiles)
    } else {
      info("Registering a built-in engine.")
      RegisterEngine.registerEngine(
        ca.common.engineJson,
        builtinEngines(ca.common.pioHome.get))
    }
  }

  def unregister(ca: ConsoleArgs): Unit = {
    RegisterEngine.unregisterEngine(ca.common.engineJson)
  }

  def train(ca: ConsoleArgs): Unit = {
    withRegisteredManifest(ca.common.engineJson) { em =>
      RunWorkflow.runWorkflow(
        ca,
        coreAssembly(ca.common.pioHome.get),
        em)
    }
  }

  def deploy(ca: ConsoleArgs): Unit = {
    withRegisteredManifest(ca.common.engineJson) { em =>
      val engineInstances = Storage.getMetaDataEngineInstances
      val engineInstance = ca.engineInstanceId map { eid =>
        engineInstances.get(eid)
      } getOrElse {
        engineInstances.getLatestCompleted(em.id, em.version)
      }
      engineInstance map { r =>
        undeploy(ca)
        RunServer.runServer(
          ca,
          coreAssembly(ca.common.pioHome.get),
          em,
          r.id)
      } getOrElse {
        ca.engineInstanceId map { eid =>
          error(
            s"Invalid engine instance ID ${ca.engineInstanceId}. Aborting.")
        } getOrElse {
          error(
            s"No valid engine instance found for engine ${em.id} " +
              s"${em.version}.\nTry running 'train' before 'deploy'. Aborting.")
        }
        sys.exit(1)
      }
    }
  }

  def dashboard(ca: ConsoleArgs): Unit = {
    info(s"Creating dashboard at ${ca.ip}:${ca.port}")
    Dashboard.createDashboard(DashboardConfig(
      ip = ca.ip,
      port = ca.port))
  }

  def eventserver(ca: ConsoleArgs): Unit = {
    info(s"Creating Event Server at ${ca.ip}:${ca.port}")
    EventServer.createEventServer(EventServerConfig(
      ip = ca.ip,
      port = ca.port))
  }

  def undeploy(ca: ConsoleArgs): Unit = {
    val serverUrl = s"http://${ca.ip}:${ca.port}"
    info(
      s"Undeploying any existing engine instance at ${serverUrl}")
    try {
      Http(s"${serverUrl}/stop").asString
    } catch {
      case e: scalaj.http.HttpException => e.code match {
        case 404 =>
          error(s"Another process is using ${serverUrl}. Aborting.")
          sys.exit(1)
      }
      case e: java.net.ConnectException =>
        warn(s"Nothing at ${serverUrl}")
    }
  }

  def compile(ca: ConsoleArgs): Unit = {
    if (!new File(ca.common.pioHome.get + File.separator + "RELEASE").exists) {
      info("Development tree detected. Building built-in engines.")

      val sbt = detectSbt(ca)
      info(s"Using command '${sbt}' at ${ca.common.pioHome.get} to build.")
      info("If the path above is incorrect, this process will fail.")

      val asm =
        if (ca.build.sbtAssemblyPackageDependency)
          " engines/assemblyPackageDependency"
        else
          ""
      val clean =
        if (ca.build.sbtClean)
          " engines/clean"
        else
          ""
      val cmd = Process(
        s"${sbt}${clean} engines/publishLocal${asm}",
        new File(ca.common.pioHome.get))
      info(s"Going to run: ${cmd}")
      try {
        val r = cmd.!(ProcessLogger(
          line => info(line), line => error(line)))
        if (r != 0) {
          error(s"Return code of previous step is ${r}. Aborting.")
          sys.exit(1)
        }
        info("Build finished successfully.")
      } catch {
        case e: java.io.IOException =>
          error(s"${e.getMessage}")
          sys.exit(1)
      }
    }

    if (builtinEngineDir) {
      info("Current working directory is a PredictionIO built-in engines " +
        "project. Skipping redundant packaging step.")
    } else {
      val sbt = detectSbt(ca)
      info(s"Using command '${sbt}' at the current working directory to build.")
      info("If the path above is incorrect, this process will fail.")
      val asm =
        if (ca.build.sbtAssemblyPackageDependency)
          " assemblyPackageDependency"
        else
          ""
      val clean = if (ca.build.sbtClean) " clean" else ""
      val buildCmd = s"${sbt} ${ca.build.sbtExtra.getOrElse("")}${clean} " +
        s"package${asm}"
      info(s"Going to run: ${buildCmd}")
      try {
        val r = buildCmd.!(ProcessLogger(
          line => info(line), line => error(line)))
        if (r != 0) {
          error(s"Return code of previous step is ${r}. Aborting.")
          sys.exit(1)
        }
        info("Build finished successfully.")
      } catch {
        case e: java.io.IOException =>
          error(s"${e.getMessage}")
          sys.exit(1)
      }
    }
  }

  def run(ca: ConsoleArgs): Unit = {
    compile(ca)

    val jarFiles = jarFilesForScala
    jarFiles foreach { f => info(s"Found JAR: ${f.getName}") }
    val allJarFiles = jarFiles ++ builtinEngines(ca.common.pioHome.get)
    val cmd = s"${getSparkHome(ca.common.sparkHome)}/bin/spark-submit --jars " +
      s"${allJarFiles.map(_.getCanonicalPath).mkString(",")} --class " +
      s"${ca.mainClass.get} ${ca.common.sparkPassThrough.mkString(" ")} " +
      coreAssembly(ca.common.pioHome.get) + " " +
      ca.common.driverPassThrough.mkString(" ")
    val proc = Process(
      cmd,
      None,
      "SPARK_YARN_USER_ENV" -> sys.env.filter(kv => kv._1.startsWith("PIO_")).
        map(kv => s"${kv._1}=${kv._2}").mkString(","))
    info(s"Submission command: ${cmd}")
    val r = proc.!
    if (r != 0) {
      error(s"Return code of previous step is ${r}. Aborting.")
      sys.exit(1)
    }
  }

  def dist(ca: ConsoleArgs): Unit = {
    if (builtinEngineDir ||
      !RegisterEngine.builtinEngine(ca.common.engineJson)) {
      compile(ca)
    }
    info("Locating files to be distributed.")
    val jarFiles = jarFilesForScala
    if (jarFiles.size == 0) {
      error("No files can be found for distribution. Aborting.")
      sys.exit(1)
    }
    val distDir = new File("dist")
    val libDir = new File(distDir, "lib")
    libDir.mkdirs
    jarFiles foreach { f =>
      info(s"Found ${f.getName}")
      FileUtils.copyFile(f, new File(libDir, f.getName))
    }
    val engineJson = "engine.json"
    FileUtils.copyFile(new File(engineJson), new File(distDir, engineJson))
    val paramsDir = new File("params")
    if (paramsDir.exists)
      FileUtils.copyDirectory(paramsDir, new File(distDir, paramsDir.getName))
    Files.createFile(distDir.toPath.resolve(distFilename))
    info(s"Successfully created distributable at: ${distDir.getCanonicalPath}")
  }

  def coreAssembly(pioHome: String): File = {
    val core = s"pio-assembly-${BuildInfo.version}.jar"
    val coreDir =
      if (new File(pioHome + File.separator + "RELEASE").exists)
        new File(pioHome + File.separator + "lib")
      else
        new File(pioHome + File.separator + "assembly")
    val coreFile = new File(coreDir, core)
    if (coreFile.exists) {
      coreFile
    } else {
      error(s"PredictionIO Core Assembly (${coreFile.getCanonicalPath}) does " +
        "not exist. Aborting.")
      sys.exit(1)
    }
  }

  def builtinEngines(pioHome: String): Seq[File] = {
    val engine = s"engines_${scalaVersionNoPatch}-${BuildInfo.version}.jar"
    val engineDeps = s"engines-assembly-${BuildInfo.version}-deps.jar"
    val engineDir =
      if (new File(pioHome + File.separator + "RELEASE").exists)
        new File(pioHome + File.separator + "lib")
      else
        new File(Seq(
          pioHome,
          "engines",
          "target",
          s"scala-${scalaVersionNoPatch}").mkString(File.separator))
    val engineFiles = Seq(
      new File(engineDir, engine),
      new File(engineDir, engineDeps))
    val allPresent = !engineFiles.exists(!_.exists)
    if (allPresent) {
      engineFiles
    } else {
      engineFiles foreach { f =>
        if (!f.exists) error(s"${f.getCanonicalPath} does not exist.")
      }
      error(s"Built-in PredictionIO engine JAR file(s) listed above is " +
        "missing. Aborting.")
      sys.exit(1)
    }
  }

  def readEngineJson(json: File): EngineManifest = {
    implicit val formats = Utils.json4sDefaultFormats +
      new EngineManifestSerializer
    try {
      read[EngineManifest](Source.fromFile(json).mkString)
    } catch {
      case e: java.io.FileNotFoundException =>
        error(s"${json.getCanonicalPath} does not exist. Aborting.")
        sys.exit(1)
      case e: MappingException =>
        error(s"${json.getCanonicalPath} has invalid content: " +
          e.getMessage)
        sys.exit(1)
    }
  }

  def withRegisteredManifest(json: File)(op: EngineManifest => Unit): Unit = {
    val ej = readEngineJson(json)
    Storage.getMetaDataEngineManifests.get(ej.id, ej.version) map {
      op
    } getOrElse {
      error(s"Engine ${ej.id} ${ej.version} is not registered.")
      error("Have you run the 'register' command yet?")
      sys.exit(1)
    }
  }

  def jarFilesAt(path: File): Array[File] = recursiveListFiles(path) filter {
    _.getName.toLowerCase.endsWith(".jar")
  }

  def jarFilesForScala: Array[File] = {
    val libFiles = jarFilesForScalaFilter(jarFilesAt(new File("lib")))
    val targetFiles = jarFilesForScalaFilter(jarFilesAt(new File("target" +
      File.separator + s"scala-${scalaVersionNoPatch}")))
    // Use libFiles is target is empty.
    if (targetFiles.size > 0) targetFiles else libFiles
  }

  def jarFilesForScalaFilter(jars: Array[File]) =
    jars.filterNot { f =>
      f.getName.toLowerCase.endsWith("-javadoc.jar") ||
      f.getName.toLowerCase.endsWith("-sources.jar")
    }

  def recursiveListFiles(f: File): Array[File] = {
    Option(f.listFiles) map { these =>
      these ++ these.filter(_.isDirectory).flatMap(recursiveListFiles)
    } getOrElse Array[File]()
  }

  def getSparkHome(sparkHome: Option[String]): String = {
    sparkHome getOrElse {
      sys.env.get("SPARK_HOME").getOrElse(".")
    }
  }

  def versionNoPatch(fullVersion: String): String = {
    val v = """^(\d+\.\d+)""".r
    val versionNoPatch = for {
      v(np) <- v findFirstIn fullVersion
    } yield np
    versionNoPatch.getOrElse(fullVersion)
  }

  def scalaVersionNoPatch: String = versionNoPatch(BuildInfo.scalaVersion)

  def detectSbt(ca: ConsoleArgs): String = {
    ca.build.sbt map {
      _.getCanonicalPath
    } getOrElse {
      val f = new File(Seq(ca.common.pioHome.get, "sbt", "sbt").mkString(
        File.separator))
      if (f.exists) f.getCanonicalPath else "sbt"
    }
  }

  def joinFile(path: Seq[String]): String =
    path.mkString(File.separator)

  def builtinEngineDir(): Boolean = {
    val engineDir = new File(".." + File.separator + "engines").getCanonicalPath
    val cwd = new File(".").getCanonicalPath
    new File(".." + File.separator + "make-distribution.sh").exists &&
      engineDir == cwd
  }

  def distEngineDir(): Boolean = new File(distFilename).exists
}
