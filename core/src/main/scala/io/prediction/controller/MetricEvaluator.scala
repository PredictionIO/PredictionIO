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

import _root_.java.io.File
import _root_.java.io.PrintWriter

import com.github.nscala_time.time.Imports.DateTime
import grizzled.slf4j.Logger
import io.prediction.annotation.DeveloperApi
import io.prediction.core.BaseEvaluator
import io.prediction.core.BaseEvaluatorResult
import io.prediction.data.storage.Storage
import io.prediction.workflow.JsonExtractor
import io.prediction.workflow.JsonExtractorOption.Both
import io.prediction.workflow.NameParamsSerializer
import io.prediction.workflow.WorkflowParams
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.json4s.native.Serialization.write
import org.json4s.native.Serialization.writePretty

import scala.language.existentials

/** Case class storing a primary score, and other scores
  *
  * @param score Primary metric score
  * @param otherScores Other scores this metric might have
  * @tparam R Type of the primary metric score
  * @group Evaluation
  */
case class MetricScores[R](
  score: R,
  otherScores: Seq[Any])

/** Contains all results of a [[MetricEvaluator]]
  *
  * @param bestScore The best score among all iterations
  * @param bestEngineParams The set of engine parameters that yielded the best score
  * @param bestIdx The index of iteration that yielded the best score
  * @param metricHeader Brief description of the primary metric score
  * @param otherMetricHeaders Brief descriptions of other metric scores
  * @param engineParamsScores All sets of engine parameters and corresponding metric scores
  * @param outputPath An optional output path where scores are saved
  * @tparam R Type of the primary metric score
  * @group Evaluation
  */
case class MetricEvaluatorResult[R](
  bestScore: MetricScores[R],
  bestEngineParams: EngineParams,
  bestIdx: Int,
  metricHeader: String,
  otherMetricHeaders: Seq[String],
  engineParamsScores: Seq[(EngineParams, MetricScores[R])],
  outputPath: Option[String])
extends BaseEvaluatorResult {

  override def toOneLiner(): String = {
    val idx = engineParamsScores.map(_._1).indexOf(bestEngineParams)
    s"Best Params Index: $idx Score: ${bestScore.score}"
  }

  override def toJSON(): String = {
    implicit lazy val formats = Utils.json4sDefaultFormats +
      new NameParamsSerializer
    write(this)
  }

  override def toHTML(): String = html.metric_evaluator().toString()

  override def toString: String = {
    implicit lazy val formats = Utils.json4sDefaultFormats +
      new NameParamsSerializer

    val bestEPStr = JsonExtractor.engineParamstoPrettyJson(Both, bestEngineParams)

    val strings = Seq(
      "MetricEvaluatorResult:",
      s"  # engine params evaluated: ${engineParamsScores.size}") ++
      Seq(
        "Optimal Engine Params:",
        s"  $bestEPStr",
        "Metrics:",
        s"  $metricHeader: ${bestScore.score}") ++
      otherMetricHeaders.zip(bestScore.otherScores).map {
        case (h, s) => s"  $h: $s"
      } ++
      outputPath.toSeq.map {
        p => s"The best variant params can be found in $p"
      }

    strings.mkString("\n")
  }
}

/** Companion object of [[MetricEvaluator]]
  *
  * @group Evaluation
  */
object MetricEvaluator {
  def apply[EI, Q, P, A, R](
    metric: Metric[EI, Q, P, A, R],
    otherMetrics: Seq[Metric[EI, Q, P, A, _]],
    outputPath: String): MetricEvaluator[EI, Q, P, A, R] = {
    new MetricEvaluator[EI, Q, P, A, R](
      metric,
      otherMetrics,
      Some(outputPath))
  }

  def apply[EI, Q, P, A, R](
    metric: Metric[EI, Q, P, A, R],
    otherMetrics: Seq[Metric[EI, Q, P, A, _]])
  : MetricEvaluator[EI, Q, P, A, R] = {
    new MetricEvaluator[EI, Q, P, A, R](
      metric,
      otherMetrics,
      None)
  }

  def apply[EI, Q, P, A, R](metric: Metric[EI, Q, P, A, R])
  : MetricEvaluator[EI, Q, P, A, R] = {
    new MetricEvaluator[EI, Q, P, A, R](
      metric,
      Seq[Metric[EI, Q, P, A, _]](),
      None)
  }

  case class NameParams(name: String, params: Params) {
    def this(np: (String, Params)) = this(np._1, np._2)
  }

  case class EngineVariant(
    id: String,
    description: String,
    engineFactory: String,
    datasource: NameParams,
    preparator: NameParams,
    algorithms: Seq[NameParams],
    serving: NameParams) {

    def this(evaluation: Evaluation, engineParams: EngineParams) = this(
      id = "",
      description = "",
      engineFactory = evaluation.getClass.getName,
      datasource = new NameParams(engineParams.dataSourceParams),
      preparator = new NameParams(engineParams.preparatorParams),
      algorithms = engineParams.algorithmParamsList.map(np => new NameParams(np)),
      serving = new NameParams(engineParams.servingParams))
  }
}

/** :: DeveloperApi ::
  * Do no use this directly. Use [[MetricEvaluator$]] instead. This is an
  * implementation of [[io.prediction.core.BaseEvaluator]] that evaluates
  * prediction performance based on metric scores.
  *
  * @param metric Primary metric
  * @param otherMetrics Other metrics
  * @param outputPath Optional output path to save evaluation results
  * @tparam EI Evaluation information type
  * @tparam Q Query class
  * @tparam P Predicted result class
  * @tparam A Actual result class
  * @tparam R Metric result class
  * @group Evaluation
  */
@DeveloperApi
class MetricEvaluator[EI, Q, P, A, R] (
  val metric: Metric[EI, Q, P, A, R],
  val otherMetrics: Seq[Metric[EI, Q, P, A, _]],
  val outputPath: Option[String])
  extends BaseEvaluator[EI, Q, P, A, MetricEvaluatorResult[R]] {
  @transient lazy val logger = Logger[this.type]
  @transient val engineInstances = Storage.getMetaDataEngineInstances()

  def saveEngineJson(
    evaluation: Evaluation,
    engineParams: EngineParams,
    outputPath: String) {

    val now = DateTime.now
    val evalClassName = evaluation.getClass.getName

    val variant = MetricEvaluator.EngineVariant(
      id = s"$evalClassName $now",
      description = "",
      engineFactory = evalClassName,
      datasource = new MetricEvaluator.NameParams(engineParams.dataSourceParams),
      preparator = new MetricEvaluator.NameParams(engineParams.preparatorParams),
      algorithms = engineParams.algorithmParamsList.map(np => new MetricEvaluator.NameParams(np)),
      serving = new MetricEvaluator.NameParams(engineParams.servingParams))

    implicit lazy val formats = Utils.json4sDefaultFormats

    logger.info(s"Writing best variant params to disk ($outputPath)...")
    val writer = new PrintWriter(new File(outputPath))
    writer.write(writePretty(variant))
    writer.close()
  }

  def evaluateBase(
    sc: SparkContext,
    evaluation: Evaluation,
    engineEvalDataSet: Seq[(EngineParams, Seq[(EI, RDD[(Q, P, A)])])],
    params: WorkflowParams): MetricEvaluatorResult[R] = {

    val evalResultList: Seq[(EngineParams, MetricScores[R])] = engineEvalDataSet
    .zipWithIndex
    .par
    .map { case ((engineParams, evalDataSet), idx) =>
      val metricScores = MetricScores[R](
        metric.calculate(sc, evalDataSet),
        otherMetrics.map(_.calculate(sc, evalDataSet)))
      (engineParams, metricScores)
    }
    .seq

    implicit lazy val formats = Utils.json4sDefaultFormats +
      new NameParamsSerializer

    evalResultList.zipWithIndex.foreach { case ((ep, r), idx) =>
      logger.info(s"Iteration $idx")
      logger.info(s"EngineParams: ${JsonExtractor.engineParamsToJson(Both, ep)}")
      logger.info(s"Result: $r")
    }

    // use max. take implicit from Metric.
    val ((bestEngineParams, bestScore), bestIdx) = evalResultList
    .zipWithIndex
    .reduce { (x, y) =>
      if (metric.compare(x._1._2.score, y._1._2.score) >= 0) x else y
    }

    // save engine params if it is set.
    outputPath.foreach { path => saveEngineJson(evaluation, bestEngineParams, path) }

    MetricEvaluatorResult(
      bestScore = bestScore,
      bestEngineParams = bestEngineParams,
      bestIdx = bestIdx,
      metricHeader = metric.header,
      otherMetricHeaders = otherMetrics.map(_.header),
      engineParamsScores = evalResultList,
      outputPath = outputPath)
  }
}
