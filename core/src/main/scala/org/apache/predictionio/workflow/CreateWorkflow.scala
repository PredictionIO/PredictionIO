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

import com.github.nscala_time.time.Imports._
import com.google.common.io.ByteStreams
import grizzled.slf4j.Logging
import org.apache.predictionio.controller.Engine
import org.apache.predictionio.core.BaseEngine
import org.apache.predictionio.data.storage.EngineInstance
import org.apache.predictionio.data.storage.EvaluationInstance
import org.apache.predictionio.data.storage.Storage
import org.apache.predictionio.workflow.JsonExtractorOption.JsonExtractorOption
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.json4s.JValue
import org.json4s.JString
import org.json4s.native.JsonMethods.parse

import scala.language.existentials

object CreateWorkflow extends Logging {

  case class WorkflowConfig(
    deployMode: String = "",
    batch: String = "",
    engineId: String = "",
    engineVersion: String = "",
    engineVariant: String = "",
    engineFactory: String = "",
    engineParamsKey: String = "",
    evaluationClass: Option[String] = None,
    engineParamsGeneratorClass: Option[String] = None,
    env: Option[String] = None,
    skipSanityCheck: Boolean = false,
    stopAfterRead: Boolean = false,
    stopAfterPrepare: Boolean = false,
    verbosity: Int = 0,
    verbose: Boolean = false,
    debug: Boolean = false,
    logFile: Option[String] = None,
    jsonExtractor: JsonExtractorOption = JsonExtractorOption.Both)

  case class AlgorithmParams(name: String, params: JValue)

  private def stringFromFile(filePath: String): String = {
    try {
      val uri = new URI(filePath)
      val fs = FileSystem.get(uri, new Configuration())
      new String(ByteStreams.toByteArray(fs.open(new Path(uri))).map(_.toChar))
    } catch {
      case e: java.io.IOException =>
        error(s"Error reading from file: ${e.getMessage}. Aborting workflow.")
        sys.exit(1)
    }
  }

  val parser = new scopt.OptionParser[WorkflowConfig]("CreateWorkflow") {
    override def errorOnUnknownArgument: Boolean = false
    opt[String]("batch") action { (x, c) =>
      c.copy(batch = x)
    } text("Batch label of the workflow run.")
    opt[String]("engine-id") required() action { (x, c) =>
      c.copy(engineId = x)
    } text("Engine's ID.")
    opt[String]("engine-version") required() action { (x, c) =>
      c.copy(engineVersion = x)
    } text("Engine's version.")
    opt[String]("engine-variant") required() action { (x, c) =>
      c.copy(engineVariant = x)
    } text("Engine variant JSON.")
    opt[String]("evaluation-class") action { (x, c) =>
      c.copy(evaluationClass = Some(x))
    } text("Class name of the run's evaluator.")
    opt[String]("engine-params-generator-class") action { (x, c) =>
      c.copy(engineParamsGeneratorClass = Some(x))
    } text("Path to evaluator parameters")
    opt[String]("env") action { (x, c) =>
      c.copy(env = Some(x))
    } text("Comma-separated list of environmental variables (in 'FOO=BAR' " +
      "format) to pass to the Spark execution environment.")
    opt[Unit]("verbose") action { (x, c) =>
      c.copy(verbose = true)
    } text("Enable verbose output.")
    opt[Unit]("debug") action { (x, c) =>
      c.copy(debug = true)
    } text("Enable debug output.")
    opt[Unit]("skip-sanity-check") action { (x, c) =>
      c.copy(skipSanityCheck = true)
    }
    opt[Unit]("stop-after-read") action { (x, c) =>
      c.copy(stopAfterRead = true)
    }
    opt[Unit]("stop-after-prepare") action { (x, c) =>
      c.copy(stopAfterPrepare = true)
    }
    opt[String]("deploy-mode") action { (x, c) =>
      c.copy(deployMode = x)
    }
    opt[Int]("verbosity") action { (x, c) =>
      c.copy(verbosity = x)
    }
    opt[String]("engine-factory") action { (x, c) =>
      c.copy(engineFactory = x)
    }
    opt[String]("engine-params-key") action { (x, c) =>
      c.copy(engineParamsKey = x)
    }
    opt[String]("log-file") action { (x, c) =>
      c.copy(logFile = Some(x))
    }
    opt[String]("json-extractor") action { (x, c) =>
      c.copy(jsonExtractor = JsonExtractorOption.withName(x))
    }
  }

  def main(args: Array[String]): Unit = {
    val wfcOpt = parser.parse(args, WorkflowConfig())
    if (wfcOpt.isEmpty) {
      logger.error("WorkflowConfig is empty. Quitting")
      return
    }

    val wfc = wfcOpt.get

    WorkflowUtils.modifyLogging(wfc.verbose)

    val evaluation = wfc.evaluationClass.map { ec =>
      try {
        WorkflowUtils.getEvaluation(ec, getClass.getClassLoader)._2
      } catch {
        case e @ (_: ClassNotFoundException | _: NoSuchMethodException) =>
          error(s"Unable to obtain evaluation $ec. Aborting workflow.", e)
          sys.exit(1)
      }
    }

    val engineParamsGenerator = wfc.engineParamsGeneratorClass.map { epg =>
      try {
        WorkflowUtils.getEngineParamsGenerator(epg, getClass.getClassLoader)._2
      } catch {
        case e @ (_: ClassNotFoundException | _: NoSuchMethodException) =>
          error(s"Unable to obtain engine parameters generator $epg. " +
            "Aborting workflow.", e)
          sys.exit(1)
      }
    }

    val pioEnvVars = wfc.env.map(e =>
      e.split(',').flatMap(p =>
        p.split('=') match {
          case Array(k, v) => List(k -> v)
          case _ => Nil
        }
      ).toMap
    ).getOrElse(Map())

    if (evaluation.isEmpty) {
      val variantJson = parse(stringFromFile(wfc.engineVariant))
      val engineFactory = if (wfc.engineFactory == "") {
        variantJson \ "engineFactory" match {
          case JString(s) => s
          case _ =>
            error("Unable to read engine factory class name from " +
              s"${wfc.engineVariant}. Aborting.")
            sys.exit(1)
        }
      } else wfc.engineFactory
      val variantId = variantJson \ "id" match {
        case JString(s) => s
        case _ =>
          error("Unable to read engine variant ID from " +
            s"${wfc.engineVariant}. Aborting.")
          sys.exit(1)
      }
      val (engineLanguage, engineFactoryObj) = try {
        WorkflowUtils.getEngine(engineFactory, getClass.getClassLoader)
      } catch {
        case e @ (_: ClassNotFoundException | _: NoSuchMethodException) =>
          error(s"Unable to obtain engine: ${e.getMessage}. Aborting workflow.")
          sys.exit(1)
      }

      val engine: BaseEngine[_, _, _, _] = engineFactoryObj()

      val customSparkConf = WorkflowUtils.extractSparkConf(variantJson)
      val workflowParams = WorkflowParams(
        verbose = wfc.verbosity,
        skipSanityCheck = wfc.skipSanityCheck,
        stopAfterRead = wfc.stopAfterRead,
        stopAfterPrepare = wfc.stopAfterPrepare,
        sparkEnv = WorkflowParams().sparkEnv ++ customSparkConf)

      // Evaluator Not Specified. Do training.
      if (!engine.isInstanceOf[Engine[_,_,_,_,_,_]]) {
        throw new NoSuchMethodException(s"Engine $engine is not trainable")
      }

      val trainableEngine = engine.asInstanceOf[Engine[_, _, _, _, _, _]]

      val engineParams = if (wfc.engineParamsKey == "") {
        trainableEngine.jValueToEngineParams(variantJson, wfc.jsonExtractor)
      } else {
        engineFactoryObj.engineParams(wfc.engineParamsKey)
      }

      val engineInstance = EngineInstance(
        id = "",
        status = "INIT",
        startTime = DateTime.now,
        endTime = DateTime.now,
        engineId = wfc.engineId,
        engineVersion = wfc.engineVersion,
        engineVariant = variantId,
        engineFactory = engineFactory,
        batch = wfc.batch,
        env = pioEnvVars,
        sparkConf = workflowParams.sparkEnv,
        dataSourceParams =
          JsonExtractor.paramToJson(wfc.jsonExtractor, engineParams.dataSourceParams),
        preparatorParams =
          JsonExtractor.paramToJson(wfc.jsonExtractor, engineParams.preparatorParams),
        algorithmsParams =
          JsonExtractor.paramsToJson(wfc.jsonExtractor, engineParams.algorithmParamsList),
        servingParams =
          JsonExtractor.paramToJson(wfc.jsonExtractor, engineParams.servingParams))

      val engineInstanceId = Storage.getMetaDataEngineInstances.insert(
        engineInstance)

      CoreWorkflow.runTrain(
        env = pioEnvVars,
        params = workflowParams,
        engine = trainableEngine,
        engineParams = engineParams,
        engineInstance = engineInstance.copy(id = engineInstanceId))
    } else {
      val workflowParams = WorkflowParams(
        verbose = wfc.verbosity,
        skipSanityCheck = wfc.skipSanityCheck,
        stopAfterRead = wfc.stopAfterRead,
        stopAfterPrepare = wfc.stopAfterPrepare,
        sparkEnv = WorkflowParams().sparkEnv)
      val evaluationInstance = EvaluationInstance(
        evaluationClass = wfc.evaluationClass.get,
        engineParamsGeneratorClass = wfc.engineParamsGeneratorClass.get,
        batch = wfc.batch,
        env = pioEnvVars,
        sparkConf = workflowParams.sparkEnv
      )
      Workflow.runEvaluation(
        evaluation = evaluation.get,
        engineParamsGenerator = engineParamsGenerator.get,
        evaluationInstance = evaluationInstance,
        params = workflowParams)
    }
  }
}
