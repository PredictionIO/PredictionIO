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

package io.prediction.workflow

import io.prediction.controller.EmptyParams
import io.prediction.controller.EngineParams
import io.prediction.controller.IEngineFactory
import io.prediction.controller.Metrics
import io.prediction.controller.Params
import io.prediction.controller.Utils
import io.prediction.controller.WorkflowParams
import io.prediction.core.Doer
import io.prediction.core.BaseMetrics
import io.prediction.data.storage.EngineInstance
import io.prediction.data.storage.Storage

import com.github.nscala_time.time.Imports._
import com.google.common.io.ByteStreams
import grizzled.slf4j.Logging
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.{ read, write }

import scala.language.existentials
import scala.reflect.Manifest
import scala.reflect.runtime.universe

import java.io.File

object CreateWorkflow extends Logging {

  case class WorkflowConfig(
    batch: String = "Transient Lazy Val",
    engineId: String = "",
    engineVersion: String = "",
    engineFactory: String = "",
    metricsClass: Option[String] = None,
    dataSourceParamsJsonPath: Option[String] = None,
    preparatorParamsJsonPath: Option[String] = None,
    algorithmsParamsJsonPath: Option[String] = None,
    servingParamsJsonPath: Option[String] = None,
    metricsParamsJsonPath: Option[String] = None,
    jsonBasePath: String = "",
    env: Option[String] = None)

  case class AlgorithmParams(name: String, params: JValue)

  implicit lazy val formats = Utils.json4sDefaultFormats

  val hadoopConf = new Configuration
  val hdfs = FileSystem.get(hadoopConf)

  private def stringFromFile(basePath: String, filePath: String): String = {
    try {
      val p =
        if (basePath == "")
          new Path(filePath)
        else
          new Path(basePath + Path.SEPARATOR + filePath)
      new String(ByteStreams.toByteArray(hdfs.open(p)).map(_.toChar))
    } catch {
      case e: java.io.IOException =>
        error(s"Error reading from file: ${e.getMessage}. Aborting workflow.")
        sys.exit(1)
    }
  }

  def main(args: Array[String]): Unit = {
    val parser = new scopt.OptionParser[WorkflowConfig]("CreateWorkflow") {
      opt[String]("batch") action { (x, c) =>
        c.copy(batch = x)
      } text("Batch label of the workflow run.")
      opt[String]("engineId") required() action { (x, c) =>
        c.copy(engineId = x)
      } text("Engine's ID.")
      opt[String]("engineVersion") required() action { (x, c) =>
        c.copy(engineVersion = x)
      } text("Engine's version.")
      opt[String]("engineFactory") required() action { (x, c) =>
        c.copy(engineFactory = x)
      } text("Class name of the engine's factory.")
      opt[String]("metricsClass") action { (x, c) =>
        c.copy(metricsClass = Some(x))
      } text("Class name of the run's metrics.")
      opt[String]("dsp") action { (x, c) =>
        c.copy(dataSourceParamsJsonPath = Some(x))
      } text("Path to data source parameters JSON file.")
      opt[String]("pp") action { (x, c) =>
        c.copy(preparatorParamsJsonPath = Some(x))
      } text("Path to preparator parameters JSON file.")
      opt[String]("ap") action { (x, c) =>
        c.copy(algorithmsParamsJsonPath = Some(x))
      } text("Path to algorithms parameters JSON file.")
      opt[String]("sp") action { (x, c) =>
        c.copy(servingParamsJsonPath = Some(x))
      } text("Path to serving parameters JSON file.")
      opt[String]("mp") action { (x, c) =>
        c.copy(metricsParamsJsonPath = Some(x))
      } text("Path to metrics parameters")
      opt[String]("jsonBasePath") action { (x, c) =>
        c.copy(jsonBasePath = x)
      } text("Base path to prepend to all parameters JSON files.")
      opt[String]("env") action { (x, c) =>
        c.copy(env = Some(x))
      } text("Comma-separated list of environmental variables (in 'FOO=BAR' " +
        "format) to pass to the Spark execution environment.")
    }

    parser.parse(args, WorkflowConfig()) map { wfc =>
      val (engineLanguage, engine) = try {
        WorkflowUtils.getEngine(wfc.engineFactory, getClass.getClassLoader)
      } catch {
        case e @ (_: ClassNotFoundException | _: NoSuchMethodException) =>
          error(s"Unable to obtain engine: ${e.getMessage}. Aborting workflow.")
          sys.exit(1)
      }
      val metrics = wfc.metricsClass.map { mc => //mc => null
        try {
          Class.forName(mc)
            .asInstanceOf[Class[BaseMetrics[_ <: Params, _, _, _, _, _, _, _ <: AnyRef]]]
        } catch {
          case e: ClassNotFoundException =>
            error("Unable to obtain metrics class object ${mc}: " +
              s"${e.getMessage}. Aborting workflow.")
            sys.exit(1)
        }
      }
      val dataSourceParams = wfc.dataSourceParamsJsonPath.map(p =>
        WorkflowUtils.extractParams(
          engineLanguage,
          stringFromFile(wfc.jsonBasePath, p),
          engine.dataSourceClass)).getOrElse(EmptyParams())
      val preparatorParams = wfc.preparatorParamsJsonPath.map(p =>
        WorkflowUtils.extractParams(
          engineLanguage,
          stringFromFile(wfc.jsonBasePath, p),
          engine.preparatorClass)).getOrElse(EmptyParams())
      val algorithmsParams: Seq[(String, Params)] =
        wfc.algorithmsParamsJsonPath.map { p =>
          val algorithmsParamsJson = parse(stringFromFile(wfc.jsonBasePath, p))
          algorithmsParamsJson match {
            case JArray(s) => s.map { algorithmParamsJValue =>
              val eap = algorithmParamsJValue.extract[AlgorithmParams]
              (
                eap.name,
                WorkflowUtils.extractParams(
                  engineLanguage,
                  compact(render(eap.params)),
                  engine.algorithmClassMap(eap.name))
              )
            }
            case _ => Nil
          }
        } getOrElse Seq(("", EmptyParams()))
      val servingParams = wfc.servingParamsJsonPath.map(p =>
        WorkflowUtils.extractParams(
          engineLanguage,
          stringFromFile(wfc.jsonBasePath, p),
          engine.servingClass)).getOrElse(EmptyParams())
      val metricsParams = wfc.metricsParamsJsonPath.map(p =>
        if (metrics.isEmpty)
          EmptyParams()
        else
          WorkflowUtils.extractParams(
            engineLanguage,
            stringFromFile(wfc.jsonBasePath, p),
            metrics.get)
      ) getOrElse EmptyParams()

      val engineParams = new EngineParams(
        dataSourceParams = dataSourceParams,
        preparatorParams = preparatorParams,
        algorithmParamsList = algorithmsParams,
        servingParams = servingParams)

      val metricsInstance = metrics
        .map(m => Doer(m, metricsParams))
        .getOrElse(null)

      val pioEnvVars = wfc.env.map(e =>
        e.split(',').flatMap(p =>
          p.split('=') match {
            case Array(k, v) => List(k -> v)
            case _ => Nil
          }
        ).toMap
      ).getOrElse(Map())

      val engineInstance = EngineInstance(
        id = "",
        status = "INIT",
        startTime = DateTime.now,
        endTime = DateTime.now,
        engineId = wfc.engineId,
        engineVersion = wfc.engineVersion,
        engineFactory = wfc.engineFactory,
        metricsClass = wfc.metricsClass.getOrElse(""),
        batch = wfc.batch,
        env = pioEnvVars,
        dataSourceParams = write(dataSourceParams),
        preparatorParams = write(preparatorParams),
        algorithmsParams = write(algorithmsParams),
        servingParams = write(servingParams),
        metricsParams = write(metricsParams),
        multipleMetricsResults = "",
        multipleMetricsResultsHTML = "",
        multipleMetricsResultsJSON = "")
      val engineInstanceId = Storage.getMetaDataEngineInstances.insert(
        engineInstance)

      CoreWorkflow.runEngineTypeless(
        env = pioEnvVars,
        params = WorkflowParams(
          verbose = 3,
          batch = wfc.batch),
        engine = engine,
        engineParams = engineParams,
        metrics = metricsInstance,
        metricsParams = metricsParams,
        engineInstance = Some(engineInstance.copy(id = engineInstanceId)))
    }
  }
}
