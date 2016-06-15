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

import io.prediction.core.BaseDataSource
import io.prediction.core.BasePreparator
import io.prediction.core.BaseAlgorithm
import io.prediction.core.BaseServing
import io.prediction.core.Doer
import io.prediction.annotation.Experimental

import grizzled.slf4j.Logger
import io.prediction.workflow.WorkflowParams
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import scala.language.implicitConversions

import _root_.java.util.NoSuchElementException

import scala.collection.mutable.{ HashMap => MutableHashMap }

/** :: Experimental ::
  * Workflow based on [[FastEvalEngine]]
  *
  * @group Evaluation
  */
@Experimental
object FastEvalEngineWorkflow  {
  @transient lazy val logger = Logger[this.type]

  type EX = Int
  type AX = Int
  type QX = Long

  case class DataSourcePrefix(dataSourceParams: (String, Params)) {
    def this(pp: PreparatorPrefix) = this(pp.dataSourceParams)
    def this(ap: AlgorithmsPrefix) = this(ap.dataSourceParams)
    def this(sp: ServingPrefix) = this(sp.dataSourceParams)
  }

  case class PreparatorPrefix(
    dataSourceParams: (String, Params),
    preparatorParams: (String, Params)) {
    def this(ap: AlgorithmsPrefix) = {
      this(ap.dataSourceParams, ap.preparatorParams)
    }
  }

  case class AlgorithmsPrefix(
    dataSourceParams: (String, Params),
    preparatorParams: (String, Params),
    algorithmParamsList: Seq[(String, Params)]) {
    def this(sp: ServingPrefix) = {
      this(sp.dataSourceParams, sp.preparatorParams, sp.algorithmParamsList)
    }
  }

  case class ServingPrefix(
    dataSourceParams: (String, Params),
    preparatorParams: (String, Params),
    algorithmParamsList: Seq[(String, Params)],
    servingParams: (String, Params)) {
    def this(ep: EngineParams) = this(
      ep.dataSourceParams,
      ep.preparatorParams,
      ep.algorithmParamsList,
      ep.servingParams)
  }

  def getDataSourceResult[TD, EI, PD, Q, P, A](
    workflow: FastEvalEngineWorkflow[TD, EI, PD, Q, P, A],
    prefix: DataSourcePrefix)
  : Map[EX, (TD, EI, RDD[(QX, (Q, A))])] = {
    val cache = workflow.dataSourceCache

    if (!cache.contains(prefix)) {
      val dataSource = Doer(
        workflow.engine.dataSourceClassMap(prefix.dataSourceParams._1),
        prefix.dataSourceParams._2)

      val result = dataSource
      .readEvalBase(workflow.sc)
      .map { case (td, ei, qaRDD) => {
        (td, ei, qaRDD.zipWithUniqueId().map(_.swap))
      }}
      .zipWithIndex
      .map(_.swap)
      .toMap

      cache += Tuple2(prefix, result)
    }
    cache(prefix)
  }

  def getPreparatorResult[TD, EI, PD, Q, P, A](
    workflow: FastEvalEngineWorkflow[TD, EI, PD, Q, P, A],
    prefix: PreparatorPrefix): Map[EX, PD] = {
    val cache = workflow.preparatorCache

    if (!cache.contains(prefix)) {
      val preparator = Doer(
        workflow.engine.preparatorClassMap(prefix.preparatorParams._1),
        prefix.preparatorParams._2)

      val result = getDataSourceResult(
        workflow = workflow,
        prefix = new DataSourcePrefix(prefix))
      .mapValues { case (td, _, _) => preparator.prepareBase(workflow.sc, td) }

      cache += Tuple2(prefix, result)
    }
    cache(prefix)
  }

  def computeAlgorithmsResult[TD, EI, PD, Q, P, A](
    workflow: FastEvalEngineWorkflow[TD, EI, PD, Q, P, A],
    prefix: AlgorithmsPrefix): Map[EX, RDD[(QX, Seq[P])]] = {

    val algoMap: Map[AX, BaseAlgorithm[PD, _, Q, P]] = prefix.algorithmParamsList
      .map { case (algoName, algoParams) => {
        try {
          Doer(workflow.engine.algorithmClassMap(algoName), algoParams)
        } catch {
          case e: NoSuchElementException => {
            val algorithmClassMap = workflow.engine.algorithmClassMap
            if (algoName == "") {
              logger.error("Empty algorithm name supplied but it could not " +
                "match with any algorithm in the engine's definition. " +
                "Existing algorithm name(s) are: " +
                s"${algorithmClassMap.keys.mkString(", ")}. Aborting.")
            } else {
              logger.error(s"${algoName} cannot be found in the engine's " +
                "definition. Existing algorithm name(s) are: " +
                s"${algorithmClassMap.keys.mkString(", ")}. Aborting.")
            }
            sys.exit(1)
          }
        }
      }}
      .zipWithIndex
      .map(_.swap)
      .toMap

    val algoCount = algoMap.size

    // Model Train
    val algoModelsMap: Map[EX, Map[AX, Any]] = getPreparatorResult(
      workflow,
      new PreparatorPrefix(prefix))
    .mapValues {
      pd => algoMap.mapValues(_.trainBase(workflow.sc,pd))
    }

    // Predict
    val dataSourceResult =
      FastEvalEngineWorkflow.getDataSourceResult(
        workflow = workflow,
        prefix = new DataSourcePrefix(prefix))

    val algoResult: Map[EX, RDD[(QX, Seq[P])]] = dataSourceResult
    .par
    .map { case (ex, (td, ei, iqaRDD)) => {
      val modelsMap: Map[AX, Any] = algoModelsMap(ex)
      val qs: RDD[(QX, Q)] = iqaRDD.mapValues(_._1)

      val algoPredicts: Seq[RDD[(QX, (AX, P))]] = (0 until algoCount)
      .map { ax => {
        val algo = algoMap(ax)
        val model = modelsMap(ax)
        val rawPredicts: RDD[(QX, P)] = algo.batchPredictBase(
          workflow.sc,
          model,
          qs)

        val predicts: RDD[(QX, (AX, P))] = rawPredicts.map {
          case (qx, p) => (qx, (ax, p))
        }
        predicts
      }}

      val unionAlgoPredicts: RDD[(QX, Seq[P])] = workflow.sc
      .union(algoPredicts)
      .groupByKey
      .mapValues { ps => {
        assert (ps.size == algoCount, "Must have same length as algoCount")
        // TODO. Check size == algoCount
        ps.toSeq.sortBy(_._1).map(_._2)
      }}
      (ex, unionAlgoPredicts)
    }}
    .seq
    .toMap

    algoResult
  }

  def getAlgorithmsResult[TD, EI, PD, Q, P, A](
    workflow: FastEvalEngineWorkflow[TD, EI, PD, Q, P, A],
    prefix: AlgorithmsPrefix): Map[EX, RDD[(QX, Seq[P])]] = {
    val cache = workflow.algorithmsCache
    if (!cache.contains(prefix)) {
      val result = computeAlgorithmsResult(workflow, prefix)
      cache += Tuple2(prefix, result)
    }
    cache(prefix)
  }

  def getServingResult[TD, EI, PD, Q, P, A](
    workflow: FastEvalEngineWorkflow[TD, EI, PD, Q, P, A],
    prefix: ServingPrefix)
  : Seq[(EI, RDD[(Q, P, A)])] = {
    val cache = workflow.servingCache
    if (!cache.contains(prefix)) {
      val serving = Doer(
        workflow.engine.servingClassMap(prefix.servingParams._1),
        prefix.servingParams._2)

      val algoPredictsMap = getAlgorithmsResult(
        workflow = workflow,
        prefix = new AlgorithmsPrefix(prefix))

      val dataSourceResult = getDataSourceResult(
        workflow = workflow,
        prefix = new DataSourcePrefix(prefix))

      val evalQAsMap = dataSourceResult.mapValues(_._3)
      val evalInfoMap = dataSourceResult.mapValues(_._2)

      val servingQPAMap: Map[EX, RDD[(Q, P, A)]] = algoPredictsMap
      .map { case (ex, psMap) => {
        val qasMap: RDD[(QX, (Q, A))] = evalQAsMap(ex)
        val qpsaMap: RDD[(QX, Q, Seq[P], A)] = psMap.join(qasMap)
        .map { case (qx, t) => (qx, t._2._1, t._1, t._2._2) }

        val qpaMap: RDD[(Q, P, A)] = qpsaMap.map {
          case (qx, q, ps, a) => (q, serving.serveBase(q, ps), a)
        }
        (ex, qpaMap)
      }}

      val servingResult = (0 until evalQAsMap.size).map { ex => {
        (evalInfoMap(ex), servingQPAMap(ex))
      }}
      .toSeq

      cache += Tuple2(prefix, servingResult)
    }
    cache(prefix)
  }

  def get[TD, EI, PD, Q, P, A](
    workflow: FastEvalEngineWorkflow[TD, EI, PD, Q, P, A],
    engineParamsList: Seq[EngineParams])
  : Seq[(EngineParams, Seq[(EI, RDD[(Q, P, A)])])] = {
    engineParamsList.map { engineParams => {
      (engineParams,
        getServingResult(workflow, new ServingPrefix(engineParams)))
    }}
  }
}

/** :: Experimental ::
  * Workflow based on [[FastEvalEngine]]
  *
  * @group Evaluation
  */
@Experimental
class FastEvalEngineWorkflow[TD, EI, PD, Q, P, A](
  val engine: FastEvalEngine[TD, EI, PD, Q, P, A],
  val sc: SparkContext,
  val workflowParams: WorkflowParams) extends Serializable {

  import io.prediction.controller.FastEvalEngineWorkflow._

  type DataSourceResult = Map[EX, (TD, EI, RDD[(QX, (Q, A))])]
  type PreparatorResult = Map[EX, PD]
  type AlgorithmsResult = Map[EX, RDD[(QX, Seq[P])]]
  type ServingResult = Seq[(EI, RDD[(Q, P, A)])]

  val dataSourceCache = MutableHashMap[DataSourcePrefix, DataSourceResult]()
  val preparatorCache = MutableHashMap[PreparatorPrefix, PreparatorResult]()
  val algorithmsCache = MutableHashMap[AlgorithmsPrefix, AlgorithmsResult]()
  val servingCache = MutableHashMap[ServingPrefix, ServingResult]()
}



/** :: Experimental ::
  * FastEvalEngine is a subclass of [[Engine]] that exploits the immutability of
  * controllers to optimize the evaluation process
  *
  * @group Evaluation
  */
@Experimental
class FastEvalEngine[TD, EI, PD, Q, P, A](
    dataSourceClassMap: Map[String, Class[_ <: BaseDataSource[TD, EI, Q, A]]],
    preparatorClassMap: Map[String, Class[_ <: BasePreparator[TD, PD]]],
    algorithmClassMap: Map[String, Class[_ <: BaseAlgorithm[PD, _, Q, P]]],
    servingClassMap: Map[String, Class[_ <: BaseServing[Q, P]]])
  extends Engine[TD, EI, PD, Q, P, A](
    dataSourceClassMap,
    preparatorClassMap,
    algorithmClassMap,
    servingClassMap) {
  @transient override lazy val logger = Logger[this.type]

  override def eval(
    sc: SparkContext,
    engineParams: EngineParams,
    params: WorkflowParams): Seq[(EI, RDD[(Q, P, A)])] = {
    logger.info("FastEvalEngine.eval")
    batchEval(sc, Seq(engineParams), params).head._2
  }

  override def batchEval(
    sc: SparkContext,
    engineParamsList: Seq[EngineParams],
    params: WorkflowParams)
  : Seq[(EngineParams, Seq[(EI, RDD[(Q, P, A)])])] = {

    val fastEngineWorkflow = new FastEvalEngineWorkflow(
      this, sc, params)

    FastEvalEngineWorkflow.get(
      fastEngineWorkflow,
      engineParamsList)
  }
}
