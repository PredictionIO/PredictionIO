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
import io.prediction.controller.Engine
import io.prediction.controller.EngineParams
import io.prediction.controller.IPersistentModel
import io.prediction.controller.LAlgorithm
import io.prediction.controller.PAlgorithm
import io.prediction.controller.Params
import io.prediction.controller.Utils
import io.prediction.controller.NiceRendering
import io.prediction.controller.java.LJavaDataSource
import io.prediction.controller.java.LJavaPreparator
import io.prediction.controller.java.LJavaAlgorithm
import io.prediction.controller.java.LJavaServing
import io.prediction.controller.java.JavaMetrics
import io.prediction.controller.java.JavaUtils
import io.prediction.controller.java.JavaEngine
import io.prediction.controller.WorkflowParams
import io.prediction.core.BaseAlgorithm
import io.prediction.core.BaseDataSource
import io.prediction.core.BaseMetrics
import io.prediction.core.BasePreparator
import io.prediction.core.BaseServing
import io.prediction.core.Doer
import io.prediction.core.LModelAlgorithm
import io.prediction.data.storage.EngineInstance
import io.prediction.data.storage.EngineInstances
import io.prediction.data.storage.Model
import io.prediction.data.storage.Storage

import com.github.nscala_time.time.Imports.DateTime
import com.twitter.chill.KryoInjection
import grizzled.slf4j.{ Logger, Logging }
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.json4s._
import org.json4s.native.Serialization.write

import scala.collection.JavaConversions._
import scala.language.existentials
import scala.reflect.ClassTag
import scala.reflect.Manifest

import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.lang.{ Iterable => JIterable }
import java.util.{ HashMap => JHashMap, Map => JMap }

// FIXME: move to better location.
object WorkflowContext extends Logging {
  def apply(
      batch: String = "",
      env: Map[String, String] = Map()): SparkContext = {
    val conf = new SparkConf().setAppName(s"PredictionIO: $batch")
    info(s"Environment received: ${env}")
    env.map(kv => conf.setExecutorEnv(kv._1, kv._2))
    info(s"SparkConf executor environment: ${conf.getExecutorEnv}")
    new SparkContext(conf)
  }
}

// skipOpt = true: use slow parallel model for prediction, requires one extra
// join stage.
class AlgoServerWrapper[Q, P, A](
    val algos: Array[_ <: BaseAlgorithm[_,_,_,Q,P]],
    val serving: BaseServing[_, Q, P],
    val skipOpt: Boolean = false,
    val verbose: Int = 0)
extends Serializable {
  @transient lazy val logger = Logger[this.type]
  // Use algo.predictBase
  def onePassPredict(
    modelIter: Iterator[(AI, Any)],
    queryActualIter: Iterator[(Q, A)])
  : Iterator[(Q, P, A)] = {
    val models = modelIter.toSeq.sortBy(_._1).map(_._2)

    queryActualIter.map{ case(query, actual) => {
      val predictions = algos.zipWithIndex.map {
        case (algo, i) => algo.predictBase(models(i), query)
      }
      val prediction = serving.serveBase(query, predictions)

      (query, prediction, actual)
    }}
  }

  // Use algo.predictBase
  def predictLocalModel(models: Seq[RDD[Any]], input: RDD[(Q, A)])
  : RDD[(Q, P, A)] = {
    if (verbose > 0) {
      logger.info("predictionLocalModel")
    }
    val sc = models.head.context
    // must have only one partition since we need all models per feature.
    // todo: duplicate indexedModel into multiple partition.
    val reInput = input.coalesce(numPartitions = 1)

    val indexedModels: Seq[RDD[(AI, Any)]] = models.zipWithIndex
      .map { case (rdd, ai) => rdd.map(m => (ai, m)) }

    val rddModel: RDD[(AI, Any)] = sc.union(indexedModels)
      .coalesce(numPartitions = 1)

    rddModel.zipPartitions(reInput)(onePassPredict)
  }

  // Use algo.batchPredictBase
  def predictParallelModel(models: Seq[Any], input: RDD[(Q, A)])
  : RDD[(Q, P, A)] = {
    if (verbose > 0) {
      logger.info("predictionParallelModel")
    }

    // Prefixed with "i" stands for "i"ndexed
    val iInput: RDD[(QI, (Q, A))] = input.zipWithUniqueId.map(_.swap)

    val iQuery: RDD[(QI, Q)] = iInput.map(e => (e._1, e._2._1))
    val sc = input.context

    // Each algo/model is run independely.
    val iAlgoPredictionSeq: Seq[RDD[(QI, (AI, P))]] = models
      .zipWithIndex
      .map { case (model, ai) => {
        algos(ai)
          .batchPredictBase(model, iQuery)
          .map{ case (fi, p) => (fi, (ai, p)) }
      }}

    val iAlgoPredictions: RDD[(QI, Seq[P])] = sc
      .union(iAlgoPredictionSeq)
      .groupByKey
      .mapValues { _.toSeq.sortBy(_._1).map(_._2) }

    val joined: RDD[(QI, (Seq[P], (Q, A)))] = iAlgoPredictions.join(iInput)

    if (verbose > 2) {
      logger.info("predictionParallelModel.before combine")
      joined.collect.foreach {  case(fi, (ps, (q, a))) => {
        val pstr = WorkflowUtils.debugString(ps)
        val qstr = WorkflowUtils.debugString(q)
        val astr = WorkflowUtils.debugString(a)
        //e => debug(WorkflowUtils.debugString(e))
        logger.info(s"I: $fi Q: $qstr A: $astr Ps: $pstr")
      }}
    }

    val combined: RDD[(QI, (Q, P, A))] = joined
    .mapValues{ case (predictions, (query, actual)) => {
      val prediction = serving.serveBase(query, predictions)
      (query, prediction, actual)
    }}

    if (verbose > 2) {
      logger.info("predictionParallelModel.after combine")
      combined.collect.foreach { case(qi, (q, p, a)) => {
        val qstr = WorkflowUtils.debugString(q)
        val pstr = WorkflowUtils.debugString(p)
        val astr = WorkflowUtils.debugString(a)
        logger.info(s"I: $qi Q: $qstr A: $astr P: $pstr")
      }}
    }

    combined.values
  }

  def predict(models: Seq[Any], input: RDD[(Q, A)])
  : RDD[(Q, P, A)] = {
    // We split the prediction into multiple mode.
    // If all algo support using local model, we will run against all of them
    // in one pass.
    val someNonLocal = algos
      //.exists(!_.isInstanceOf[LAlgorithm[_, _, _, Q, P]])
      .exists(!_.isInstanceOf[LModelAlgorithm[_, Q, P]])

    if (!someNonLocal && !skipOpt) {
      // When algo is local, the model is the only element in RDD[M].
      val localModelAlgo = algos
        .map(_.asInstanceOf[LModelAlgorithm[_, Q, P]])
        //.map(_.asInstanceOf[LAlgorithm[_, _, _, Q, P]])
      val rddModels = localModelAlgo.zip(models)
        .map{ case (algo, model) => algo.getModel(model) }
      predictLocalModel(rddModels, input)
    } else {
      predictParallelModel(models, input)
    }
  }
}

class MetricsWrapper[
    MDP, MQ, MP, MA, MU: ClassTag, MR, MMR <: AnyRef](
    val metrics: BaseMetrics[_,MDP,MQ,MP,MA,MU,MR,MMR])
extends Serializable {
  def computeUnit[Q, P, A](input: RDD[(Q, P, A)]): RDD[MU] = {
    input
    .map{ e => (
      e._1.asInstanceOf[MQ],
      e._2.asInstanceOf[MP],
      e._3.asInstanceOf[MA]) }
    .map(metrics.computeUnitBase)
  }

  def computeSet[DP](input: (DP, Iterable[MU])): (MDP, MR) = {
    val mdp = input._1.asInstanceOf[MDP]
    val results = metrics.computeSetBase(mdp, input._2.toSeq)
    (mdp, results)
  }

  def computeMultipleSets(input: Array[(MDP, MR)]): MMR = {
    // maybe sort them.
    metrics.computeMultipleSetsBase(input)
  }
}

object CoreWorkflow {
  @transient lazy val logger = Logger[this.type]
  @transient lazy val engineInstanceStub = EngineInstance(
    id = "",
    status = "INIT",
    startTime = DateTime.now,
    endTime = DateTime.now,
    engineId = "",
    engineVersion = "",
    engineFactory = "",
    metricsClass = "",
    batch = "",
    env = Map(),
    dataSourceParams = "",
    preparatorParams = "",
    algorithmsParams = "",
    servingParams = "",
    metricsParams = "",
    multipleMetricsResults = "",
    multipleMetricsResultsHTML = "",
    multipleMetricsResultsJSON = "")

  // ***Do not directly call*** any "Typeless" method unless you know exactly
  // what you are doing.

  // When engine and metrics are instantiated direcly from CLI, the compiler has
  // no way to know their actual type parameter during compile time. To rememdy
  // this restriction, we have to let engine and metrics to be casted to their
  // own type parameters, and force cast their type during runtime.
  // In particular, metrics needs to be instantiated to keep scala compiler
  // happy.
  def runEngineTypeless[
      DP, TD, PD, Q, P, A,
      MDP, MQ, MP, MA,
      MU, MR, MMR <: AnyRef
      ](
      engine: Engine[TD, DP, PD, Q, P, A],
      engineParams: EngineParams,
      metrics
        : BaseMetrics[_ <: Params, MDP, MQ, MP, MA, MU, MR, MMR] = null,
      metricsParams: Params = EmptyParams(),
      engineInstance: Option[EngineInstance] = None,
      env: Map[String, String] = WorkflowUtils.pioEnvVars,
      params: WorkflowParams = WorkflowParams()
    ) {

    runTypeless(
      env = env,
      params = params,
      dataSourceClassOpt = Some(engine.dataSourceClass),
      dataSourceParams = engineParams.dataSourceParams,
      preparatorClassOpt = Some(engine.preparatorClass),
      preparatorParams = engineParams.preparatorParams,
      algorithmClassMapOpt = Some(engine.algorithmClassMap),
      algorithmParamsList = engineParams.algorithmParamsList,
      servingClassOpt = Some(engine.servingClass),
      servingParams = engineParams.servingParams,
      metricsClassOpt = (if (metrics == null) None else Some(metrics.getClass)),
      metricsParams = metricsParams,
      engineInstance = engineInstance
    )(
      JavaUtils.fakeClassTag[MU],
      JavaUtils.fakeClassTag[MR],
      JavaUtils.fakeClassTag[MMR])
  }

  // yipjustin: The parameter list has more than 80 columns. But I cannot find a
  // way to spread it to multiple lines while presving the reability.
  def runTypeless[
      DP, TD, PD, Q, P, A,
      MDP, MQ, MP, MA,
      MU : ClassTag, MR : ClassTag, MMR <: AnyRef :ClassTag
      ](
      dataSourceClassOpt
        : Option[Class[_ <: BaseDataSource[_ <: Params, DP, TD, Q, A]]] = None,
      dataSourceParams: Params = EmptyParams(),
      preparatorClassOpt
        : Option[Class[_ <: BasePreparator[_ <: Params, TD, PD]]] = None,
      preparatorParams: Params = EmptyParams(),
      algorithmClassMapOpt
        : Option[Map[String, Class[_ <: BaseAlgorithm[_ <: Params, PD, _, Q, P]]]]
        = None,
      algorithmParamsList: Seq[(String, Params)] = null,
      servingClassOpt: Option[Class[_ <: BaseServing[_ <: Params, Q, P]]]
        = None,
      servingParams: Params = EmptyParams(),
      metricsClassOpt
        : Option[Class[_ <: BaseMetrics[_ <: Params, MDP, MQ, MP, MA, MU, MR, MMR]]]
        = None,
      metricsParams: Params = EmptyParams(),
      engineInstance: Option[EngineInstance] = None,
      env: Map[String, String] = WorkflowUtils.pioEnvVars,
      params: WorkflowParams = WorkflowParams()
    ) {
    logger.info("CoreWorkflow.run")
    logger.info("Start spark context")

    metricsClassOpt.map(_ => WorkflowUtils.checkUpgrade("evaluation")).
      getOrElse(WorkflowUtils.checkUpgrade("training"))

    val verbose = params.verbose

    val sc = WorkflowContext(params.batch, env)

    // Create an engine instance even for runner as well
    implicit val f = Utils.json4sDefaultFormats
    val realEngineInstance: EngineInstance = engineInstance getOrElse {
      val i = engineInstanceStub.copy(
        startTime = DateTime.now,
        engineFactory = getClass.getName,
        metricsClass = metricsClassOpt.map(_.getName).getOrElse(""),
        batch = params.batch,
        env = env,
        dataSourceParams = write(dataSourceParams),
        preparatorParams = write(preparatorParams),
        algorithmsParams = write(algorithmParamsList),
        servingParams = write(servingParams),
        metricsParams = write(metricsParams))
      val iid = Storage.getMetaDataEngineInstances.insert(i)
      i.copy(id = iid)
    }

    //if (dataSourceClass == null || dataSourceParams == null) {
    if (dataSourceClassOpt.isEmpty) {
      logger.info("Dataprep Class or Params is null. Stop here");
      return
    }

    logger.info("Data Source")
    val dataSource = Doer(dataSourceClassOpt.get, dataSourceParams)

    val evalParamsDataMap
    : Map[EI, (DP, TD, RDD[(Q, A)])] = dataSource
      .readBase(sc)
      .zipWithIndex
      .map(_.swap)
      .toMap

    val localParamsSet: Map[EI, DP] = evalParamsDataMap.map {
      case(ei, e) => (ei -> e._1)
    }

    val evalDataMap: Map[EI, (TD, RDD[(Q, A)])] = evalParamsDataMap.map {
      case(ei, e) => (ei -> (e._2, e._3))
    }

    logger.info(s"Number of training set: ${localParamsSet.size}")

    if (verbose > 2) {
      evalDataMap.foreach{ case (ei, data) => {
        val (trainingData, testingData) = data
        //val collectedValidationData = testingData.collect
        val trainingDataStr = WorkflowUtils.debugString(trainingData)
        val testingDataStrs = testingData.collect
          .map(WorkflowUtils.debugString)

        logger.info(s"Data Set $ei")
        logger.info(s"Params: ${localParamsSet(ei)}")
        logger.info(s"TrainingData:")
        logger.info(trainingDataStr)
        logger.info(s"TestingData: (count=${testingDataStrs.length})")
        testingDataStrs.foreach { logger.info(_) }
      }}
    }

    logger.info("Data source complete")

    if (preparatorClassOpt.isEmpty) {
      logger.info("Preparator is null. Stop here")
      return
    }

    logger.info("Preparator")
    val preparator = Doer(preparatorClassOpt.get, preparatorParams)

    val evalPreparedMap: Map[EI, PD] = evalDataMap
    .map{ case (ei, data) => (ei, preparator.prepareBase(sc, data._1)) }

    if (verbose > 2) {
      evalPreparedMap.foreach{ case (ei, pd) => {
        val s = WorkflowUtils.debugString(pd)
        logger.info(s"Prepared Data Set $ei")
        logger.info(s"Params: ${localParamsSet(ei)}")
        logger.info(s"PreparedData: $s")
      }}
    }

    logger.info("Preparator complete")

    if (algorithmClassMapOpt.isEmpty) {
      logger.info("Algo is null. Stop here")
      return
    }

    logger.info("Algo model construction")

    // Instantiate algos
    val algoInstanceList: Array[BaseAlgorithm[_, PD, _, Q, P]] =
    algorithmParamsList
      .map {
        case (algoName, algoParams) =>
          try {
            Doer(algorithmClassMapOpt.get(algoName), algoParams)
          } catch {
            case e: java.util.NoSuchElementException =>
              if (algoName == "")
                logger.error("Empty algorithm name supplied but it could not " +
                  "match with any algorithm in the engine's definition. " +
                  "Existing algorithm name(s) are: " +
                  s"${algorithmClassMapOpt.get.keys.mkString(", ")}. Aborting.")
              else
                logger.error(s"${algoName} cannot be found in the engine's " +
                  "definition. Existing algorithm name(s) are: " +
                  s"${algorithmClassMapOpt.get.keys.mkString(", ")}. Aborting.")
              sys.exit(1)
          }
      }
      .toArray

    if (algoInstanceList.length == 0) {
      logger.info("AlgoList has zero length. Stop here")
      return
    }

    // Model Training
    // Since different algo can have different model data, have to use Any.
    // We need to use par here. Since this process allows algorithms to perform
    // "Actions"
    // (see https://spark.apache.org/docs/latest/programming-guide.html#actions)
    // on RDDs. Meaning that algo may kick off a spark pipeline to for training.
    // Hence, we parallelize this process.
    val evalAlgoModelMap: Map[EI, Seq[(AI, Any)]] = evalPreparedMap
    .par
    .map { case (ei, preparedData) => {

      val algoModelSeq: Seq[(AI, Any)] = algoInstanceList
      .zipWithIndex
      .map { case (algo, index) => {
        val model: Any = algo.trainBase(sc, preparedData)
        (index, model)
      }}

      (ei, algoModelSeq)
    }}
    .seq
    .toMap

    if (verbose > 2) {
      evalAlgoModelMap.map{ case(ei, aiModelSeq) => {
        aiModelSeq.map { case(ai, model) => {
          logger.info(s"Model ei: $ei ai: $ai")
          logger.info(WorkflowUtils.debugString(model))
        }}
      }}
    }

    if (servingClassOpt.isEmpty) {
      logger.info("Serving is null. Stop here")
      return
    }
    val serving = Doer(servingClassOpt.get, servingParams)

    logger.info("Algo prediction")

    val evalPredictionMap
    : Map[EI, RDD[(Q, P, A)]] = evalDataMap.map { case (ei, data) => {
      val validationData: RDD[(Q, A)] = data._2
      val algoModel: Seq[Any] = evalAlgoModelMap(ei)
        .sortBy(_._1)
        .map(_._2)

      val algoServerWrapper = new AlgoServerWrapper[Q, P, A](
        algoInstanceList, serving, skipOpt = false, verbose = verbose)
      (ei, algoServerWrapper.predict(algoModel, validationData))
    }}
    .toMap

    if (verbose > 2) {
      evalPredictionMap.foreach{ case(ei, fpaRdd) => {
        logger.info(s"Prediction $ei $fpaRdd")
        fpaRdd.collect.foreach{ case(f, p, a) => {
          val fs = WorkflowUtils.debugString(f)
          val ps = WorkflowUtils.debugString(p)
          val as = WorkflowUtils.debugString(a)
          logger.info(s"F: $fs P: $ps A: $as")
        }}
      }}
    }

    if (verbose > 0) {
      evalPredictionMap.foreach { case(ei, fpaRdd) => {
        val n = fpaRdd.count()
        logger.info(s"DP $ei has $n rows")
      }}
    }

    //val models: Seq[Seq[Any]] = extractPersistentModels(realEngineInstance,
    //  evalAlgoModelMap, algorithmParamsList, algoInstanceList)

    if (metricsClassOpt.isEmpty) {
      logger.info("Metrics is null. Stop here")
      val models: Seq[Seq[Any]] = extractPersistentModels(
        sc,
        realEngineInstance,
        evalAlgoModelMap,
        algorithmParamsList,
        algoInstanceList,
        params
      )

      saveEngineInstance(
        realEngineInstance,
        algorithmParamsList,
        algoInstanceList,
        models,
        None)
      return
    }

    val metrics = Doer(metricsClassOpt.get, metricsParams)
    val metricsWrapper = new MetricsWrapper(metrics)

    // Metrics Unit
    val evalMetricsUnitMap: Map[Int, RDD[MU]] =
      evalPredictionMap.mapValues(metricsWrapper.computeUnit)

    if (verbose > 2) {
      evalMetricsUnitMap.foreach{ case(i, e) => {
        val estr = WorkflowUtils.debugString(e)
        logger.info(s"MetricsUnit: i=$i e=$estr")
      }}
    }

    // Metrics Set
    val evalMetricsResultsMap
    : Map[EI, RDD[(MDP, MR)]] = evalMetricsUnitMap
    .map{ case (ei, metricsUnits) => {
      val metricsResults
      : RDD[(MDP, MR)] = metricsUnits
        // shuffle must be true, otherwise all upstream stage will be forced to
        // use a single partition.
        .coalesce(numPartitions=1, shuffle = true)
        .glom()
        .map(e => (localParamsSet(ei), e.toIterable))
        .map(metricsWrapper.computeSet)

      (ei, metricsResults)
    }}

    if (verbose > 2) {
      evalMetricsResultsMap.foreach{ case(ei, e) => {
        val estr = WorkflowUtils.debugString(e)
        logger.info(s"MetricsResults $ei $estr")
      }}
    }

    val multipleMetricsResults: RDD[MMR] = sc
      .union(evalMetricsResultsMap.values.toVector)
      .coalesce(numPartitions=1, shuffle = true)
      .glom()
      .map(metricsWrapper.computeMultipleSets)

    val metricsOutput: Array[MMR] = multipleMetricsResults.collect

    logger.info(s"DataSourceParams: $dataSourceParams")
    logger.info(s"PreparatorParams: $preparatorParams")
    algorithmParamsList.zipWithIndex.foreach { case (ap, ai) => {
      logger.info(s"Algo: $ai Name: ${ap._1} Params: ${ap._2}")
    }}
    logger.info(s"ServingParams: $servingParams")
    logger.info(s"MetricsParams: $metricsParams")

    metricsOutput foreach { logger.info(_) }

    logger.info("CoreWorkflow.run completed.")

    val models: Seq[Seq[Any]] = extractPersistentModels(
      sc,
      realEngineInstance,
      evalAlgoModelMap,
      algorithmParamsList,
      algoInstanceList,
      params)

    saveEngineInstance(
      realEngineInstance,
      algorithmParamsList,
      algoInstanceList,
      models,
      Some(metricsOutput.head))
  }

  /** Extract model for persistent layer.
    *
    * PredictionIO presist models for future use.  It allows custom
    * implementation for persisting models. You need to implement the
    * [[io.prediction.controller.IPersistentModel]] interface. This method
    * traverses all models in the workflow. If the model is a
    * [[io.prediction.controller.IPersistentModel]], it calls the save method
    * for custom persistence logic.
    *
    * For model doesn't support custom logic, PredictionIO serializes the whole
    * model if the corresponding algorithm is local. On the other hand, if the
    * model is parallel (i.e. model associated with a number of huge RDDS), this
    * method return Unit, in which case PredictionIO will retrain the whole
    * model from scratch next time it is used.
    */
  def extractPersistentModels[PD, Q, P](
    sc: SparkContext,
    realEngineInstance: EngineInstance,
    evalAlgoModelMap: Map[EI, Seq[(AI, Any)]],
    algorithmParamsList: Seq[(String, Params)],
    algoInstanceList: Array[BaseAlgorithm[_, PD, _, Q, P]],
    params: WorkflowParams
  ): Seq[Seq[Any]] = {

    def getPersistentModel(model: Any, instanceId: String, algoParams: Params)
    : Any = {
      if (model.asInstanceOf[IPersistentModel[Params]].save(
          realEngineInstance.id, algoParams, sc))
        PersistentModelManifest(className = model.getClass.getName)
      else
        Unit
    }

    val evalIds: Seq[EI] = evalAlgoModelMap.keys.toSeq.sorted

    // Two cases. If params.saveModel is true, it will attempts to collect all
    // model in all evaluations; if it is false, it will create the same
    // array size with Unit.

    // Case where saveModel == false, return early.
    if (!params.saveModel) {
      return evalIds.map(ei => evalAlgoModelMap(ei).map(_ => Unit))
    }

    // Below code handles the case where saveModel == true
    // Notice that the following code runs in parallel (.par) as collect is a
    // blocking call.
    evalIds
    .par
    .map { ei =>
      val algoModels: Seq[(AI, Any)] = evalAlgoModelMap(ei).sortBy(_._1)
      algoModels
      .par
      .map { case(ai, model) =>
        val algo = algoInstanceList(ai)
        val algoParams = algorithmParamsList(ai)._2

        // Parallel Model
        if (algo.isInstanceOf[PAlgorithm[_, _, _, _, _]]) {
          if (model.isInstanceOf[IPersistentModel[_]]) {
            getPersistentModel(model, realEngineInstance.id, algoParams)
          } else {
            Unit
          }
        } else {  // Local Model
          // Local Model is wrap inside a single RDD object
          val m = model.asInstanceOf[RDD[Any]]
            .coalesce(numPartitions = 1, shuffle = true)
            .collect
            .head
          if (m.isInstanceOf[IPersistentModel[_]]) {
            getPersistentModel(m, realEngineInstance.id, algoParams)
          } else {
            m
          }
        }
      }
      .seq
    }
    .seq
  }

  def saveEngineInstance[
      PD, Q, P,
      MMR <: AnyRef : ClassTag
      ](
      realEngineInstance: EngineInstance,
      algorithmParamsList: Seq[(String, Params)],
      algoInstanceList: Array[BaseAlgorithm[_, PD, _, Q, P]],
      models: Seq[Seq[Any]],
      mmr: Option[MMR]
      ): String = {
    implicit val f = Utils.json4sDefaultFormats

    val translatedAlgorithmsParams = write(
      algorithmParamsList.zip(algoInstanceList).map {
        case ((name, params), inst) =>
          if (inst.isInstanceOf[LJavaAlgorithm[_, _, _, _, _]])
            (name -> WorkflowUtils.javaObjectToJValue(params))
          else
            (name -> params)
      })
    Storage.getModelDataModels.insert(Model(
      id = realEngineInstance.id,
      models = KryoInjection(models)))
    val engineInstances = Storage.getMetaDataEngineInstances

    val (multipleMetricsResultsHTML, multipleMetricsResultsJSON) =
      mmr.map ( mmr =>
        if (mmr.isInstanceOf[NiceRendering]) {
          val niceRenderingResult = mmr.asInstanceOf[NiceRendering]
          (niceRenderingResult.toHTML, niceRenderingResult.toJSON)
        } else {
          logger.warn(
            s"${mmr.getClass.getName} is not a NiceRendering instance.")
          ("", "")
        }
      ).getOrElse(("", ""))

    engineInstances.update(realEngineInstance.copy(
      status = mmr.map(_ => "EVALCOMPLETED").getOrElse("COMPLETED"),
      endTime = DateTime.now,
      algorithmsParams = translatedAlgorithmsParams,
      multipleMetricsResults = mmr.map(_.toString).getOrElse(""),
      multipleMetricsResultsHTML = multipleMetricsResultsHTML,
      multipleMetricsResultsJSON = multipleMetricsResultsJSON
      ))

    logger.info(s"Saved engine instance with ID: ${realEngineInstance.id}")
    realEngineInstance.id
  }

}




/*
Ideally, Java could also act as other scala base class. But the tricky part
is in the algorithmClassMap, where javac is not smart enough to match
JMap[String, Class[_ <: LJavaAlgorith[...]]] (which is provided by the
caller) with JMap[String, Class[_ <: BaseAlgo[...]]] (signature of this
function). If we change the caller to use Class[_ <: BaseAlgo[...]], it is
difficult for the engine builder, as we wrap data structures with RDD in the
base class. Hence, we have to sacrifices here, that all Doers calling
JavaCoreWorkflow needs to be Java sub-doers.
*/
object JavaCoreWorkflow {
  def noneIfNull[T](t: T): Option[T] = (if (t == null) None else Some(t))

  // Java doesn't support default parameters. If you only want to test, say,
  // DataSource and PreparatorClass only, please pass null to the other
  // components.
  // Another method is to use JavaEngineBuilder, add only the components you
  // already have. It will handle the missing ones.
      //DSP <: Params, PP <: Params, SP <: Params, MP <: Params,
  def run[
      DP, TD, PD, Q, P, A, MU, MR, MMR <: AnyRef](
    env: JMap[String, String] = new JHashMap(),
    dataSourceClass: Class[_ <: BaseDataSource[_ <: Params, DP, TD, Q, A]],
    dataSourceParams: Params,
    preparatorClass: Class[_ <: BasePreparator[_ <: Params, TD, PD]],
    preparatorParams: Params,
    algorithmClassMap:
      JMap[String, Class[_ <: BaseAlgorithm[_ <: Params, PD, _, Q, P]]],
    algorithmParamsList: JIterable[(String, Params)],
    servingClass: Class[_ <: BaseServing[_ <: Params, Q, P]],
    servingParams: Params,
    metricsClass: Class[_ <: BaseMetrics[_ <: Params, DP, Q, P, A, MU, MR, MMR]],
    metricsParams: Params,
    params: WorkflowParams
  ) = {

    val scalaAlgorithmClassMap = (
      if (algorithmClassMap == null) null
      else Map(algorithmClassMap.toSeq:_ *))

    val scalaAlgorithmParamsList = (
      if (algorithmParamsList == null) null
      else algorithmParamsList.toSeq)

    CoreWorkflow.runTypeless(
      env = mapAsScalaMap(env).toMap,
      params = params,
      dataSourceClassOpt = noneIfNull(dataSourceClass),
      dataSourceParams = dataSourceParams,
      preparatorClassOpt = noneIfNull(preparatorClass),
      preparatorParams = preparatorParams,
      algorithmClassMapOpt = noneIfNull(scalaAlgorithmClassMap),
      algorithmParamsList = scalaAlgorithmParamsList,
      servingClassOpt = noneIfNull(servingClass),
      servingParams = servingParams,
      metricsClassOpt = noneIfNull(metricsClass),
      metricsParams = metricsParams
    )(
      JavaUtils.fakeClassTag[MU],
      JavaUtils.fakeClassTag[MR],
      JavaUtils.fakeClassTag[MMR])

  }
}
