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


package org.apache.predictionio.workflow

import java.io.Serializable

import com.twitter.bijection.Injection
import com.twitter.chill.{KryoBase, KryoInjection, ScalaKryoInstantiator}
import de.javakaffee.kryoserializers.SynchronizedCollectionsSerializer
import grizzled.slf4j.Logging
import org.apache.predictionio.controller.{Engine, Utils}
import org.apache.predictionio.core.{BaseAlgorithm, BaseServing, Doer}
import org.apache.predictionio.data.storage.{EngineInstance, Storage}
import org.apache.predictionio.workflow.JsonExtractorOption.JsonExtractorOption
import org.apache.predictionio.workflow.CleanupFunctions
import org.apache.spark.rdd.RDD
import org.json4s._
import org.json4s.native.JsonMethods._
import scala.language.existentials

case class BatchPredictConfig(
  inputFilePath: String = "batchpredict-input.json",
  outputFilePath: String = "batchpredict-output.json",
  queryPartitions: Option[Int] = None,
  engineInstanceId: String = "",
  engineId: Option[String] = None,
  engineVersion: Option[String] = None,
  engineVariant: String = "",
  env: Option[String] = None,
  verbose: Boolean = false,
  debug: Boolean = false,
  jsonExtractor: JsonExtractorOption = JsonExtractorOption.Both)

object BatchPredict extends Logging {

  class KryoInstantiator(classLoader: ClassLoader) extends ScalaKryoInstantiator {
    override def newKryo(): KryoBase = {
      val kryo = super.newKryo()
      kryo.setClassLoader(classLoader)
      SynchronizedCollectionsSerializer.registerSerializers(kryo)
      kryo
    }
  }

  object KryoInstantiator extends Serializable {
    def newKryoInjection : Injection[Any, Array[Byte]] = {
      val kryoInstantiator = new KryoInstantiator(getClass.getClassLoader)
      KryoInjection.instance(kryoInstantiator)
    }
  }

  val engineInstances = Storage.getMetaDataEngineInstances
  val modeldata = Storage.getModelDataModels

  def main(args: Array[String]): Unit = {
    val parser = new scopt.OptionParser[BatchPredictConfig]("BatchPredict") {
      opt[String]("input") action { (x, c) =>
        c.copy(inputFilePath = x)
      } text("Path to file containing input queries; a " +
        "multi-object JSON file with one object per line.")
      opt[String]("output") action { (x, c) =>
        c.copy(outputFilePath = x)
      } text("Path to file containing output predictions; a " +
        "multi-object JSON file with one object per line.")
      opt[Int]("query-partitions") action { (x, c) =>
        c.copy(queryPartitions = Some(x))
      } text("Limit concurrency of predictions by setting the number " +
        "of partitions used internally for the RDD of queries.")
      opt[String]("engineId") action { (x, c) =>
        c.copy(engineId = Some(x))
      } text("Engine ID.")
      opt[String]("engineId") action { (x, c) =>
        c.copy(engineId = Some(x))
      } text("Engine ID.")
      opt[String]("engineVersion") action { (x, c) =>
        c.copy(engineVersion = Some(x))
      } text("Engine version.")
      opt[String]("engine-variant") required() action { (x, c) =>
        c.copy(engineVariant = x)
      } text("Engine variant JSON.")
      opt[String]("env") action { (x, c) =>
        c.copy(env = Some(x))
      } text("Comma-separated list of environmental variables (in 'FOO=BAR' " +
        "format) to pass to the Spark execution environment.")
      opt[String]("engineInstanceId") required() action { (x, c) =>
        c.copy(engineInstanceId = x)
      } text("Engine instance ID.")
      opt[Unit]("verbose") action { (x, c) =>
        c.copy(verbose = true)
      } text("Enable verbose output.")
      opt[Unit]("debug") action { (x, c) =>
        c.copy(debug = true)
      } text("Enable debug output.")
      opt[String]("json-extractor") action { (x, c) =>
        c.copy(jsonExtractor = JsonExtractorOption.withName(x))
      }
    }

    parser.parse(args, BatchPredictConfig()) map { config =>
      WorkflowUtils.modifyLogging(config.verbose)
      engineInstances.get(config.engineInstanceId) map { engineInstance =>

        val engine = getEngine(engineInstance)

        run(config, engineInstance, engine)

      } getOrElse {
        error(s"Invalid engine instance ID. Aborting batch predict.")
      }
    }
  }

  def getEngine(engineInstance: EngineInstance): Engine[_, _, _, _, _, _] = {

    val engineFactoryName = engineInstance.engineFactory

    val (engineLanguage, engineFactory) =
      WorkflowUtils.getEngine(engineFactoryName, getClass.getClassLoader)
    val maybeEngine = engineFactory()

    // EngineFactory return a base engine, which may not be deployable.
    maybeEngine match {
      case e: Engine[_, _, _, _, _, _] => e
      case _ => throw new NoSuchMethodException(
        s"Engine $maybeEngine cannot be used for batch predict")
    }
  }

  def run[Q, P](
    config: BatchPredictConfig,
    engineInstance: EngineInstance,
    engine: Engine[_, _, _, Q, P, _]): Unit = {

    try {
      val engineParams = engine.engineInstanceToEngineParams(
        engineInstance, config.jsonExtractor)

      val kryo = KryoInstantiator.newKryoInjection

      val modelsFromEngineInstance =
        kryo.invert(modeldata.get(engineInstance.id).get.models).get.
        asInstanceOf[Seq[Any]]

      val prepareSparkContext = WorkflowContext(
        batch = engineInstance.engineFactory,
        executorEnv = engineInstance.env,
        mode = "Batch Predict (model)",
        sparkEnv = engineInstance.sparkConf)

      val models = engine.prepareDeploy(
        prepareSparkContext,
        engineParams,
        engineInstance.id,
        modelsFromEngineInstance,
        params = WorkflowParams()
      )

      val algorithms = engineParams.algorithmParamsList.map { case (n, p) =>
        Doer(engine.algorithmClassMap(n), p)
      }

      val servingParamsWithName = engineParams.servingParams

      val serving = Doer(engine.servingClassMap(servingParamsWithName._1),
        servingParamsWithName._2)

      val runSparkContext = WorkflowContext(
        batch = engineInstance.engineFactory,
        executorEnv = engineInstance.env,
        mode = "Batch Predict (runner)",
        sparkEnv = engineInstance.sparkConf)

      val inputRDD: RDD[String] = runSparkContext.
        textFile(config.inputFilePath).
        filter(_.trim.nonEmpty)
      val queriesRDD: RDD[String] = config.queryPartitions match {
        case Some(p) => inputRDD.repartition(p)
        case None => inputRDD
      }

      val predictionsRDD: RDD[String] = queriesRDD.map { queryString =>
        val jsonExtractorOption = config.jsonExtractor
        // Extract Query from Json
        val query = JsonExtractor.extract(
          jsonExtractorOption,
          queryString,
          algorithms.head.queryClass,
          algorithms.head.querySerializer,
          algorithms.head.gsonTypeAdapterFactories
        )
        // Deploy logic. First call Serving.supplement, then Algo.predict,
        // finally Serving.serve.
        val supplementedQuery = serving.supplementBase(query)
        // TODO: Parallelize the following.
        val predictions = algorithms.zip(models).map { case (a, m) =>
          a.predictBase(m, supplementedQuery)
        }
        // Notice that it is by design to call Serving.serve with the
        // *original* query.
        val prediction = serving.serveBase(query, predictions)
        // Combine query with prediction, so the batch results are
        // self-descriptive.
        val predictionJValue = JsonExtractor.toJValue(
          jsonExtractorOption,
          Map("query" -> query,
              "prediction" -> prediction),
          algorithms.head.querySerializer,
          algorithms.head.gsonTypeAdapterFactories)
        // Return JSON string
        compact(render(predictionJValue))
      }

      predictionsRDD.saveAsTextFile(config.outputFilePath)

    } finally {
      CleanupFunctions.run()
    }
  }
}
