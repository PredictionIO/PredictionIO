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

package io.prediction.controller

import grizzled.slf4j.Logger
import io.prediction.core.BaseAlgorithm
import io.prediction.core.BaseDataSource
import io.prediction.core.BaseEngine
import io.prediction.core.BasePreparator
import io.prediction.core.BaseServing
import io.prediction.core.Doer
import io.prediction.data.storage.EngineInstance
import io.prediction.data.storage.StorageClientException
import io.prediction.workflow.CreateWorkflow
import io.prediction.workflow.EngineLanguage
import io.prediction.workflow.JsonExtractorOption.JsonExtractorOption
import io.prediction.workflow.NameParamsSerializer
import io.prediction.workflow.PersistentModelManifest
import io.prediction.workflow.SparkWorkflowUtils
import io.prediction.workflow.StopAfterPrepareInterruption
import io.prediction.workflow.StopAfterReadInterruption
import io.prediction.workflow.WorkflowParams
import io.prediction.workflow.WorkflowUtils
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.read

import scala.collection.JavaConversions
import scala.language.implicitConversions

/** This class chains up the entire data process. PredictionIO uses this
  * information to create workflows and deployments. In Scala, you should
  * implement an object that extends the [[EngineFactory]] trait similar to the
  * following example.
  *
  * {{{
  * object ItemRankEngine extends EngineFactory {
  *   def apply() = {
  *     new Engine(
  *       classOf[ItemRankDataSource],
  *       classOf[ItemRankPreparator],
  *       Map(
  *         "knn" -> classOf[KNNAlgorithm],
  *         "rand" -> classOf[RandomAlgorithm],
  *         "mahoutItemBased" -> classOf[MahoutItemBasedAlgorithm]),
  *       classOf[ItemRankServing])
  *   }
  * }
  * }}}
  *
  * @see [[EngineFactory]]
  * @tparam TD Training data class.
  * @tparam EI Evaluation info class.
  * @tparam PD Prepared data class.
  * @tparam Q Input query class.
  * @tparam P Output prediction class.
  * @tparam A Actual value class.
  * @param dataSourceClassMap Map of data source names to class.
  * @param preparatorClassMap Map of preparator names to class.
  * @param algorithmClassMap Map of algorithm names to classes.
  * @param servingClassMap Map of serving names to class.
  * @group Engine
  */
class Engine[TD, EI, PD, Q, P, A](
    val dataSourceClassMap: Map[String,
      Class[_ <: BaseDataSource[TD, EI, Q, A]]],
    val preparatorClassMap: Map[String, Class[_ <: BasePreparator[TD, PD]]],
    val algorithmClassMap: Map[String, Class[_ <: BaseAlgorithm[PD, _, Q, P]]],
    val servingClassMap: Map[String, Class[_ <: BaseServing[Q, P]]])
  extends BaseEngine[EI, Q, P, A] {

  private[prediction]
  implicit lazy val formats = Utils.json4sDefaultFormats +
    new NameParamsSerializer

  @transient lazy protected val logger = Logger[this.type]

  /** This auxiliary constructor is provided for backward compatibility.
    *
    * @param dataSourceClass Data source class.
    * @param preparatorClass Preparator class.
    * @param algorithmClassMap Map of algorithm names to classes.
    * @param servingClass Serving class.
    */
  def this(
    dataSourceClass: Class[_ <: BaseDataSource[TD, EI, Q, A]],
    preparatorClass: Class[_ <: BasePreparator[TD, PD]],
    algorithmClassMap: Map[String, Class[_ <: BaseAlgorithm[PD, _, Q, P]]],
    servingClass: Class[_ <: BaseServing[Q, P]]) = this(
      Map("" -> dataSourceClass),
      Map("" -> preparatorClass),
      algorithmClassMap,
      Map("" -> servingClass)
    )

  /** Java-friendly constructor
    *
    * @param dataSourceClass Data source class.
    * @param preparatorClass Preparator class.
    * @param algorithmClassMap Map of algorithm names to classes.
    * @param servingClass Serving class.
    */
  def this(dataSourceClass: Class[_ <: BaseDataSource[TD, EI, Q, A]],
    preparatorClass: Class[_ <: BasePreparator[TD, PD]],
    algorithmClassMap: _root_.java.util.Map[String, Class[_ <: BaseAlgorithm[PD, _, Q, P]]],
    servingClass: Class[_ <: BaseServing[Q, P]]) = this(
    Map("" -> dataSourceClass),
    Map("" -> preparatorClass),
    JavaConversions.mapAsScalaMap(algorithmClassMap).toMap,
    Map("" -> servingClass)
  )

  /** Returns a new Engine instance, mimicking case class's copy method behavior.
    */
  def copy(
    dataSourceClassMap: Map[String, Class[_ <: BaseDataSource[TD, EI, Q, A]]]
      = dataSourceClassMap,
    preparatorClassMap: Map[String, Class[_ <: BasePreparator[TD, PD]]]
      = preparatorClassMap,
    algorithmClassMap: Map[String, Class[_ <: BaseAlgorithm[PD, _, Q, P]]]
      = algorithmClassMap,
    servingClassMap: Map[String, Class[_ <: BaseServing[Q, P]]]
      = servingClassMap): Engine[TD, EI, PD, Q, P, A] = {
    new Engine(
      dataSourceClassMap,
      preparatorClassMap,
      algorithmClassMap,
      servingClassMap)
  }

  /** Training this engine would return a list of models.
    *
    * @param sc An instance of SparkContext.
    * @param engineParams An instance of [[EngineParams]] for running a single training.
    * @param params An instance of [[WorkflowParams]] that controls the workflow.
    * @return A list of models.
    */
  def train(
      sc: SparkContext,
      engineParams: EngineParams,
      engineInstanceId: String,
      params: WorkflowParams): Seq[Any] = {
    val (dataSourceName, dataSourceParams) = engineParams.dataSourceParams
    val dataSource = Doer(dataSourceClassMap(dataSourceName), dataSourceParams)

    val (preparatorName, preparatorParams) = engineParams.preparatorParams
    val preparator = Doer(preparatorClassMap(preparatorName), preparatorParams)

    val algoParamsList = engineParams.algorithmParamsList
    require(
      algoParamsList.size > 0,
      "EngineParams.algorithmParamsList must have at least 1 element.")

    val algorithms = algoParamsList.map { case (algoName, algoParams) =>
      Doer(algorithmClassMap(algoName), algoParams)
    }

    val models = Engine.train(
      sc, dataSource, preparator, algorithms, params)

    val algoCount = algorithms.size
    val algoTuples: Seq[(String, Params, BaseAlgorithm[_, _, _, _], Any)] =
    (0 until algoCount).map { ax => {
      // val (name, params) = algoParamsList(ax)
      val (name, params) = algoParamsList(ax)
      (name, params, algorithms(ax), models(ax))
    }}

    makeSerializableModels(
      sc,
      engineInstanceId = engineInstanceId,
      algoTuples = algoTuples)
  }

  /** Algorithm models can be persisted before deploy. However, it is also
    * possible that models are not persisted. This method retrains non-persisted
    * models and return a list of models that can be used directly in deploy.
    */
  private[prediction]
  def prepareDeploy(
    sc: SparkContext,
    engineParams: EngineParams,
    engineInstanceId: String,
    persistedModels: Seq[Any],
    params: WorkflowParams): Seq[Any] = {

    val algoParamsList = engineParams.algorithmParamsList
    val algorithms = algoParamsList.map { case (algoName, algoParams) =>
      Doer(algorithmClassMap(algoName), algoParams)
    }

    val models = if (persistedModels.exists(m => m.isInstanceOf[Unit.type])) {
      // If any of persistedModels is Unit, we need to re-train the model.
      logger.info("Some persisted models are Unit, need to re-train.")
      val (dataSourceName, dataSourceParams) = engineParams.dataSourceParams
      val dataSource = Doer(dataSourceClassMap(dataSourceName), dataSourceParams)

      val (preparatorName, preparatorParams) = engineParams.preparatorParams
      val preparator = Doer(preparatorClassMap(preparatorName), preparatorParams)

      val td = dataSource.readTrainingBase(sc)
      val pd = preparator.prepareBase(sc, td)

      val models = algorithms.zip(persistedModels).map { case (algo, m) =>
        m match {
          case Unit => algo.trainBase(sc, pd)
          case _ => m
        }
      }
      models
    } else {
      logger.info("Using persisted model")
      persistedModels
    }

    models
    .zip(algorithms)
    .zip(algoParamsList)
    .zipWithIndex
    .map {
      case (((model, algo), (algoName, algoParams)), ax) => {
        model match {
          case modelManifest: PersistentModelManifest => {
            logger.info("Custom-persisted model detected for algorithm " +
              algo.getClass.getName)
            SparkWorkflowUtils.getPersistentModel(
              modelManifest,
              Seq(engineInstanceId, ax, algoName).mkString("-"),
              algoParams,
              Some(sc),
              getClass.getClassLoader)
          }
          case m => {
            try {
              logger.info(
                s"Loaded model ${m.getClass.getName} for algorithm " +
                s"${algo.getClass.getName}")
              sc.stop
              m
            } catch {
              case e: NullPointerException =>
                logger.warn(
                  s"Null model detected for algorithm ${algo.getClass.getName}")
                m
            }
          }
        }  // model match
      }
    }
  }

  /** Extract model for persistent layer.
    *
    * PredictionIO presist models for future use. It allows custom
    * implementation for persisting models. You need to implement the
    * [[io.prediction.controller.PersistentModel]] interface. This method
    * traverses all models in the workflow. If the model is a
    * [[io.prediction.controller.PersistentModel]], it calls the save method
    * for custom persistence logic.
    *
    * For model doesn't support custom logic, PredictionIO serializes the whole
    * model if the corresponding algorithm is local. On the other hand, if the
    * model is parallel (i.e. model associated with a number of huge RDDS), this
    * method return Unit, in which case PredictionIO will retrain the whole
    * model from scratch next time it is used.
    */
  private def makeSerializableModels(
    sc: SparkContext,
    engineInstanceId: String,
    // AlgoName, Algo, Model
    algoTuples: Seq[(String, Params, BaseAlgorithm[_, _, _, _], Any)]
  ): Seq[Any] = {

    logger.info(s"engineInstanceId=$engineInstanceId")

    algoTuples
    .zipWithIndex
    .map { case ((name, params, algo, model), ax) =>
      algo.makePersistentModel(
        sc = sc,
        modelId = Seq(engineInstanceId, ax, name).mkString("-"),
        algoParams = params,
        bm = model)
    }
  }

  /** This is implemented such that [[io.prediction.controller.Evaluation]] can
    * use this method to generate inputs for [[io.prediction.controller.Metric]].
    *
    * @param sc An instance of SparkContext.
    * @param engineParams An instance of [[EngineParams]] for running a single evaluation.
    * @param params An instance of [[WorkflowParams]] that controls the workflow.
    * @return A list of evaluation information and RDD of query, predicted
    *         result, and actual result tuple tuple.
    */
  def eval(
    sc: SparkContext,
    engineParams: EngineParams,
    params: WorkflowParams)
  : Seq[(EI, RDD[(Q, P, A)])] = {
    val (dataSourceName, dataSourceParams) = engineParams.dataSourceParams
    val dataSource = Doer(dataSourceClassMap(dataSourceName), dataSourceParams)

    val (preparatorName, preparatorParams) = engineParams.preparatorParams
    val preparator = Doer(preparatorClassMap(preparatorName), preparatorParams)

    val algoParamsList = engineParams.algorithmParamsList
    require(
      algoParamsList.size > 0,
      "EngineParams.algorithmParamsList must have at least 1 element.")

    val algorithms = algoParamsList.map { case (algoName, algoParams) => {
      try {
        Doer(algorithmClassMap(algoName), algoParams)
      } catch {
        case e: NoSuchElementException => {
          if (algoName == "") {
            logger.error("Empty algorithm name supplied but it could not " +
              "match with any algorithm in the engine's definition. " +
              "Existing algorithm name(s) are: " +
              s"${algorithmClassMap.keys.mkString(", ")}. Aborting.")
          } else {
            logger.error(s"$algoName cannot be found in the engine's " +
              "definition. Existing algorithm name(s) are: " +
              s"${algorithmClassMap.keys.mkString(", ")}. Aborting.")
          }
          sys.exit(1)
        }
      }
    }}

    val (servingName, servingParams) = engineParams.servingParams
    val serving = Doer(servingClassMap(servingName), servingParams)

    Engine.eval(sc, dataSource, preparator, algorithms, serving)
  }

  override def jValueToEngineParams(
    variantJson: JValue,
    jsonExtractor: JsonExtractorOption): EngineParams = {

    val engineLanguage = EngineLanguage.Scala
    // Extract EngineParams
    logger.info(s"Extracting datasource params...")
    val dataSourceParams: (String, Params) =
      WorkflowUtils.getParamsFromJsonByFieldAndClass(
        variantJson,
        "datasource",
        dataSourceClassMap,
        engineLanguage,
        jsonExtractor)
    logger.info(s"Datasource params: $dataSourceParams")

    logger.info(s"Extracting preparator params...")
    val preparatorParams: (String, Params) =
      WorkflowUtils.getParamsFromJsonByFieldAndClass(
        variantJson,
        "preparator",
        preparatorClassMap,
        engineLanguage,
        jsonExtractor)
    logger.info(s"Preparator params: $preparatorParams")

    val algorithmsParams: Seq[(String, Params)] =
      variantJson findField {
        case JField("algorithms", _) => true
        case _ => false
      } map { jv =>
        val algorithmsParamsJson = jv._2
        algorithmsParamsJson match {
          case JArray(s) => s.map { algorithmParamsJValue =>
            val eap = algorithmParamsJValue.extract[CreateWorkflow.AlgorithmParams]
            (
              eap.name,
              WorkflowUtils.extractParams(
                engineLanguage,
                compact(render(eap.params)),
                algorithmClassMap(eap.name),
                jsonExtractor)
            )
          }
          case _ => Nil
        }
      } getOrElse Seq(("", EmptyParams()))

    logger.info(s"Extracting serving params...")
    val servingParams: (String, Params) =
      WorkflowUtils.getParamsFromJsonByFieldAndClass(
        variantJson,
        "serving",
        servingClassMap,
        engineLanguage,
        jsonExtractor)
    logger.info(s"Serving params: $servingParams")

    new EngineParams(
      dataSourceParams = dataSourceParams,
      preparatorParams = preparatorParams,
      algorithmParamsList = algorithmsParams,
      servingParams = servingParams)
  }

  private[prediction] def engineInstanceToEngineParams(
    engineInstance: EngineInstance,
    jsonExtractor: JsonExtractorOption): EngineParams = {

    implicit val formats = DefaultFormats
    val engineLanguage = EngineLanguage.Scala

    val dataSourceParamsWithName: (String, Params) = {
      val (name, params) =
        read[(String, JValue)](engineInstance.dataSourceParams)
      if (!dataSourceClassMap.contains(name)) {
        logger.error(s"Unable to find datasource class with name '$name'" +
          " defined in Engine.")
        sys.exit(1)
      }
      val extractedParams = WorkflowUtils.extractParams(
        engineLanguage,
        compact(render(params)),
        dataSourceClassMap(name),
        jsonExtractor)
      (name, extractedParams)
    }

    val preparatorParamsWithName: (String, Params) = {
      val (name, params) =
        read[(String, JValue)](engineInstance.preparatorParams)
      if (!preparatorClassMap.contains(name)) {
        logger.error(s"Unable to find preparator class with name '$name'" +
          " defined in Engine.")
        sys.exit(1)
      }
      val extractedParams = WorkflowUtils.extractParams(
        engineLanguage,
        compact(render(params)),
        preparatorClassMap(name),
        jsonExtractor)
      (name, extractedParams)
    }

    val algorithmsParamsWithNames =
      read[Seq[(String, JValue)]](engineInstance.algorithmsParams).map {
        case (algoName, params) =>
          val extractedParams = WorkflowUtils.extractParams(
            engineLanguage,
            compact(render(params)),
            algorithmClassMap(algoName),
            jsonExtractor)
          (algoName, extractedParams)
      }

    val servingParamsWithName: (String, Params) = {
      val (name, params) = read[(String, JValue)](engineInstance.servingParams)
      if (!servingClassMap.contains(name)) {
        logger.error(s"Unable to find serving class with name '$name'" +
          " defined in Engine.")
        sys.exit(1)
      }
      val extractedParams = WorkflowUtils.extractParams(
        engineLanguage,
        compact(render(params)),
        servingClassMap(name),
        jsonExtractor)
      (name, extractedParams)
    }

    new EngineParams(
      dataSourceParams = dataSourceParamsWithName,
      preparatorParams = preparatorParamsWithName,
      algorithmParamsList = algorithmsParamsWithNames,
      servingParams = servingParamsWithName)
  }
}

/** This object contains concrete implementation for some methods of the
  * [[Engine]] class.
  *
  * @group Engine
  */
object Engine {
  private type EX = Int
  private type AX = Int
  private type QX = Long

  @transient lazy private val logger = Logger[this.type]

  /** Helper class to accept either a single data source, or a map of data
    * sources, with a companion object providing implicit conversions, so
    * using this class directly is not necessary.
    *
    * @tparam TD Training data class
    * @tparam EI Evaluation information class
    * @tparam Q Input query class
    * @tparam A Actual result class
    */
  class DataSourceMap[TD, EI, Q, A](
    val m: Map[String, Class[_ <: BaseDataSource[TD, EI, Q, A]]]) {
    def this(c: Class[_ <: BaseDataSource[TD, EI, Q, A]]) = this(Map("" -> c))
  }

  /** Companion object providing implicit conversions, so using this directly
    * is not necessary.
    */
  object DataSourceMap {
    implicit def cToMap[TD, EI, Q, A](
      c: Class[_ <: BaseDataSource[TD, EI, Q, A]]):
      DataSourceMap[TD, EI, Q, A] = new DataSourceMap(c)
    implicit def mToMap[TD, EI, Q, A](
      m: Map[String, Class[_ <: BaseDataSource[TD, EI, Q, A]]]):
      DataSourceMap[TD, EI, Q, A] = new DataSourceMap(m)
  }

  /** Helper class to accept either a single preparator, or a map of
    * preparators, with a companion object providing implicit conversions, so
    * using this class directly is not necessary.
    *
    * @tparam TD Training data class
    * @tparam PD Prepared data class
    */
  class PreparatorMap[TD, PD](
    val m: Map[String, Class[_ <: BasePreparator[TD, PD]]]) {
    def this(c: Class[_ <: BasePreparator[TD, PD]]) = this(Map("" -> c))
  }

  /** Companion object providing implicit conversions, so using this directly
    * is not necessary.
    */
  object PreparatorMap {
    implicit def cToMap[TD, PD](
      c: Class[_ <: BasePreparator[TD, PD]]):
      PreparatorMap[TD, PD] = new PreparatorMap(c)
    implicit def mToMap[TD, PD](
      m: Map[String, Class[_ <: BasePreparator[TD, PD]]]):
      PreparatorMap[TD, PD] = new PreparatorMap(m)
  }

  /** Helper class to accept either a single serving, or a map of serving, with
    * a companion object providing implicit conversions, so using this class
    * directly is not necessary.
    *
    * @tparam Q Input query class
    * @tparam P Predicted result class
    */
  class ServingMap[Q, P](
    val m: Map[String, Class[_ <: BaseServing[Q, P]]]) {
    def this(c: Class[_ <: BaseServing[Q, P]]) = this(Map("" -> c))
  }

  /** Companion object providing implicit conversions, so using this directly
    * is not necessary.
    */
  object ServingMap {
    implicit def cToMap[Q, P](
      c: Class[_ <: BaseServing[Q, P]]): ServingMap[Q, P] =
        new ServingMap(c)
    implicit def mToMap[Q, P](
      m: Map[String, Class[_ <: BaseServing[Q, P]]]): ServingMap[Q, P] =
        new ServingMap(m)
  }

  /** Convenient method for returning an instance of [[Engine]].
    *
    * @param dataSourceMap Accepts either an instance of Class of the data
    *                      source, or a Map of data source classes (implicitly
    *                      converted to [[DataSourceMap]].
    * @param preparatorMap Accepts either an instance of Class of the
    *                      preparator, or a Map of preparator classes
    *                      (implicitly converted to [[PreparatorMap]].
    * @param algorithmClassMap Accepts a Map of algorithm classes.
    * @param servingMap Accepts either an instance of Class of the serving, or
    *                   a Map of serving classes (implicitly converted to
    *                   [[ServingMap]].
    * @tparam TD Training data class
    * @tparam EI Evaluation information class
    * @tparam PD Prepared data class
    * @tparam Q Input query class
    * @tparam P Predicted result class
    * @tparam A Actual result class
    * @return An instance of [[Engine]]
    */
  def apply[TD, EI, PD, Q, P, A](
    dataSourceMap: DataSourceMap[TD, EI, Q, A],
    preparatorMap: PreparatorMap[TD, PD],
    algorithmClassMap: Map[String, Class[_ <: BaseAlgorithm[PD, _, Q, P]]],
    servingMap: ServingMap[Q, P]): Engine[TD, EI, PD, Q, P, A] = new Engine(
      dataSourceMap.m,
      preparatorMap.m,
      algorithmClassMap,
      servingMap.m
    )

  /** Provides concrete implementation of training for [[Engine]].
    *
    * @param sc An instance of SparkContext
    * @param dataSource An instance of data source
    * @param preparator An instance of preparator
    * @param algorithmList A list of algorithm instances
    * @param params An instance of [[WorkflowParams]] that controls the training
    *               process.
    * @tparam TD Training data class
    * @tparam PD Prepared data class
    * @tparam Q Input query class
    * @return A list of trained models
    */
  def train[TD, PD, Q](
      sc: SparkContext,
      dataSource: BaseDataSource[TD, _, Q, _],
      preparator: BasePreparator[TD, PD],
      algorithmList: Seq[BaseAlgorithm[PD, _, Q, _]],
      params: WorkflowParams
    ): Seq[Any] = {
    logger.info("EngineWorkflow.train")
    logger.info(s"DataSource: $dataSource")
    logger.info(s"Preparator: $preparator")
    logger.info(s"AlgorithmList: $algorithmList")

    if (params.skipSanityCheck) {
      logger.info("Data sanity check is off.")
    } else {
      logger.info("Data sanity check is on.")
    }

    val td = try {
      dataSource.readTrainingBase(sc)
    } catch {
      case e: StorageClientException =>
        logger.error(s"Error occured reading from data source. (Reason: " +
          e.getMessage + ") Please see the log for debugging details.", e)
        sys.exit(1)
    }

    if (!params.skipSanityCheck) {
      td match {
        case sanityCheckable: SanityCheck => {
          logger.info(s"${td.getClass.getName} supports data sanity" +
            " check. Performing check.")
          sanityCheckable.sanityCheck()
        }
        case _ => {
          logger.info(s"${td.getClass.getName} does not support" +
            " data sanity check. Skipping check.")
        }
      }
    }

    if (params.stopAfterRead) {
      logger.info("Stopping here because --stop-after-read is set.")
      throw StopAfterReadInterruption()
    }

    val pd = preparator.prepareBase(sc, td)

    if (!params.skipSanityCheck) {
      pd match {
        case sanityCheckable: SanityCheck => {
          logger.info(s"${pd.getClass.getName} supports data sanity" +
            " check. Performing check.")
          sanityCheckable.sanityCheck()
        }
        case _ => {
          logger.info(s"${pd.getClass.getName} does not support" +
            " data sanity check. Skipping check.")
        }
      }
    }

    if (params.stopAfterPrepare) {
      logger.info("Stopping here because --stop-after-prepare is set.")
      throw StopAfterPrepareInterruption()
    }

    val models: Seq[Any] = algorithmList.map(_.trainBase(sc, pd))

    if (!params.skipSanityCheck) {
      models.foreach { model => {
        model match {
          case sanityCheckable: SanityCheck => {
            logger.info(s"${model.getClass.getName} supports data sanity" +
              " check. Performing check.")
            sanityCheckable.sanityCheck()
          }
          case _ => {
            logger.info(s"${model.getClass.getName} does not support" +
              " data sanity check. Skipping check.")
          }
        }
      }}
    }

    logger.info("EngineWorkflow.train completed")
    models
  }

  /** Provides concrete implementation of evaluation for [[Engine]].
    *
    * @param sc An instance of SparkContext
    * @param dataSource An instance of data source
    * @param preparator An instance of preparator
    * @param algorithmList A list of algorithm instances
    * @param serving An instance of serving
    * @tparam TD Training data class
    * @tparam PD Prepared data class
    * @tparam Q Input query class
    * @tparam P Predicted result class
    * @tparam A Actual result class
    * @tparam EI Evaluation information class
    * @return A list of evaluation information, RDD of query, predicted result,
    *         and actual result tuple tuple.
    */
  def eval[TD, PD, Q, P, A, EI](
      sc: SparkContext,
      dataSource: BaseDataSource[TD, EI, Q, A],
      preparator: BasePreparator[TD, PD],
      algorithmList: Seq[BaseAlgorithm[PD, _, Q, P]],
      serving: BaseServing[Q, P]): Seq[(EI, RDD[(Q, P, A)])] = {
    logger.info(s"DataSource: $dataSource")
    logger.info(s"Preparator: $preparator")
    logger.info(s"AlgorithmList: $algorithmList")
    logger.info(s"Serving: $serving")

    val algoMap: Map[AX, BaseAlgorithm[PD, _, Q, P]] = algorithmList
      .zipWithIndex
      .map(_.swap)
      .toMap
    val algoCount = algoMap.size

    val evalTupleMap: Map[EX, (TD, EI, RDD[(Q, A)])] = dataSource
      .readEvalBase(sc)
      .zipWithIndex
      .map(_.swap)
      .toMap

    val evalCount = evalTupleMap.size

    val evalTrainMap: Map[EX, TD] = evalTupleMap.mapValues(_._1)
    val evalInfoMap: Map[EX, EI] = evalTupleMap.mapValues(_._2)
    val evalQAsMap: Map[EX, RDD[(QX, (Q, A))]] = evalTupleMap
      .mapValues(_._3)
      .mapValues{ _.zipWithUniqueId().map(_.swap) }

    val preparedMap: Map[EX, PD] = evalTrainMap.mapValues { td => {
      preparator.prepareBase(sc, td)
    }}

    val algoModelsMap: Map[EX, Map[AX, Any]] = preparedMap.mapValues { pd => {
      algoMap.mapValues(_.trainBase(sc,pd))
    }}

    val suppQAsMap: Map[EX, RDD[(QX, (Q, A))]] = evalQAsMap.mapValues { qas =>
      qas.map { case (qx, (q, a)) => (qx, (serving.supplementBase(q), a)) }
    }

    val algoPredictsMap: Map[EX, RDD[(QX, Seq[P])]] = (0 until evalCount)
    .map { ex => {
      val modelMap: Map[AX, Any] = algoModelsMap(ex)

      val qs: RDD[(QX, Q)] = suppQAsMap(ex).mapValues(_._1)

      val algoPredicts: Seq[RDD[(QX, (AX, P))]] = (0 until algoCount)
      .map { ax => {
        val algo = algoMap(ax)
        val model = modelMap(ax)
        val rawPredicts: RDD[(QX, P)] = algo.batchPredictBase(sc, model, qs)
        val predicts: RDD[(QX, (AX, P))] = rawPredicts.map { case (qx, p) => {
          (qx, (ax, p))
        }}
        predicts
      }}

      val unionAlgoPredicts: RDD[(QX, Seq[P])] = sc.union(algoPredicts)
      .groupByKey()
      .mapValues { ps => {
        assert (ps.size == algoCount, "Must have same length as algoCount")
        // TODO. Check size == algoCount
        ps.toSeq.sortBy(_._1).map(_._2)
      }}

      (ex, unionAlgoPredicts)
    }}
    .toMap

    val servingQPAMap: Map[EX, RDD[(Q, P, A)]] = algoPredictsMap
    .map { case (ex, psMap) => {
      // The query passed to serving.serve is the original one, not
      // supplemented.
      val qasMap: RDD[(QX, (Q, A))] = evalQAsMap(ex)
      val qpsaMap: RDD[(QX, Q, Seq[P], A)] = psMap.join(qasMap)
      .map { case (qx, t) => (qx, t._2._1, t._1, t._2._2) }

      val qpaMap: RDD[(Q, P, A)] = qpsaMap.map {
        case (qx, q, ps, a) => (q, serving.serveBase(q, ps), a)
      }
      (ex, qpaMap)
    }}

    (0 until evalCount).map { ex => {
      (evalInfoMap(ex), servingQPAMap(ex))
    }}
    .toSeq
  }
}

/** Mix in this trait for queries that contain prId (PredictedResultId).
  * This is useful when your engine expects queries to also be associated with
  * prId keys when feedback loop is enabled.
  *
  * @group Helper
  */
@deprecated("To be removed in future releases.", "0.9.2")
trait WithPrId {
  val prId: String = ""
}
