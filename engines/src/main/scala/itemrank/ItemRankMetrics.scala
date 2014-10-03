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

package io.prediction.engines.itemrank

import io.prediction.controller.Params
import io.prediction.controller.Metrics

import com.github.nscala_time.time.Imports._
import scala.math.BigDecimal
import breeze.stats.{ mean, meanAndVariance }

class MetricsParams(
  val verbose: Boolean // print report
) extends Params {}

// param for preparing training
class TrainingDataParams(
    val appid: Int,
    val itypes: Option[Set[String]],
    // action for training
    val actions: Set[String],
    // actions within this startUntil time will be included in training
    // use all data if None
    val startUntil: Option[Tuple2[DateTime, DateTime]],
    val verbose: Boolean // for debug purpose
  ) extends Serializable {
    override def toString = s"${appid} ${itypes} ${actions} ${startUntil}"
  }

// param for preparing evaluation data
class ValidationDataParams(
    val appid: Int,
    val itypes: Option[Set[String]],
    // actions within this startUntil time will be included in validation
    val startUntil: Tuple2[DateTime, DateTime],
    val goal: Set[String] // action name
  ) extends Serializable {
    override def toString = s"${appid} ${itypes} ${startUntil} ${goal}"
  }

class DataParams(
  val tdp: TrainingDataParams,
  val vdp: ValidationDataParams,
  val name: String = ""
) extends Params with HasName {
  override def toString = s"TDP: ${tdp} VDP: ${vdp}"
}

class ItemRankMetrics(mp: MetricsParams)
  extends Metrics[MetricsParams,
    DataParams, Query, Prediction, Actual,
      MetricUnit, MetricResult, MultipleMetricResult] {

  override def computeUnit(query: Query, prediction: Prediction,
    actual: Actual): MetricUnit  = {

    val k = query.iids.size

    new MetricUnit(
      q = query,
      p = prediction,
      a = actual,
      score = averagePrecisionAtK(k, prediction.items.map(_._1),
        actual.iids.toSet),
      baseline = averagePrecisionAtK(k, query.iids,
        actual.iids.toSet))
  }

  // calcualte MAP at k
  override def computeSet(dataParams: DataParams,
    metricUnits: Seq[MetricUnit]): MetricResult = {

    val algoMVC = meanAndVariance(metricUnits.map(_.score))
    val baselineMVC = meanAndVariance(metricUnits.map(_.baseline))

    //val mean = metricUnits.map( eu => eu.score ).sum / metricUnits.size
    //val baseMean = metricUnits.map (eu => eu.baseline).sum /
    //  metricUnits.size

    if (mp.verbose) {
      val reports = metricUnits.map{ eu =>
        val flag = if (eu.baseline > eu.score) "x" else ""
        Seq(eu.q.uid, eu.q.iids.mkString(","),
          eu.p.items.map(p => s"${p._1}(${printDouble(p._2)})").mkString(","),
          eu.a.iids.mkString(","),
          printDouble(eu.baseline), printDouble(eu.score),
          flag)
      }.map { x => x.map(t => s"[${t}]")}

      println("result:")
      println(s"${dataParams.tdp.startUntil}")
      println(s"${dataParams.vdp.startUntil}")
      println("uid - basline - ranked - actual - baseline score - algo score")
      reports.foreach { r =>
        println(s"${r.mkString(" ")}")
      }
      println(
        s"baseline MAP@k = ${baselineMVC.mean} (${baselineMVC.stdDev}), " +
        s"algo MAP@k = ${algoMVC.mean} (${algoMVC.stdDev})")
    }

    new MetricResult(
      testStartUntil = dataParams.vdp.startUntil,
      baselineMean = baselineMVC.mean,
      baselineStdev = baselineMVC.stdDev,
      algoMean = algoMVC.mean,
      algoStdev = algoMVC.stdDev)
  }

  override def computeMultipleSets(
    input: Seq[(DataParams, MetricResult)]): MultipleMetricResult = {

    val mrSeq = input.map(_._2)
    // calculate mean of MAP@k of each validion result
    val algoMVC = meanAndVariance(mrSeq.map(_.algoMean))

    val baselineMVC = meanAndVariance(mrSeq.map(_.baselineMean))

    if (mp.verbose) {
      mrSeq.sortBy(_.testStartUntil).foreach { mr =>
        println(s"${mr.testStartUntil}")
        println(
          s"baseline MAP@k = ${mr.baselineMean} (${mr.baselineStdev}), " +
          s"algo MAP@k = ${mr.algoMean} (${mr.algoStdev})")
      }
      println(
        s"baseline average = ${baselineMVC.mean} (${baselineMVC.stdDev}), " +
        s"algo average = ${algoMVC.mean} (${algoMVC.stdDev})")
    }

    new MultipleMetricResult (
      baselineMean = baselineMVC.mean,
      baselineStdev = baselineMVC.stdDev,
      algoMean = algoMVC.mean,
      algoStdev = algoMVC.stdDev)
  }

  private def printDouble(d: Double): String = {
    BigDecimal(d).setScale(4, BigDecimal.RoundingMode.HALF_UP).toString
  }

  // metric
  private def averagePrecisionAtK[T](k: Int, p: Seq[T], r: Set[T]): Double = {
    // supposedly the predictedItems.size should match k
    // NOTE: what if predictedItems is less than k? use the avaiable items as k.
    val n = scala.math.min(p.size, k)

    // find if each element in the predictedItems is one of the relevant items
    // if so, map to 1. else map to 0
    // (0, 1, 0, 1, 1, 0, 0)
    val rBin: Seq[Int] = p.take(n).map { x => if (r(x)) 1 else 0 }
    val pAtKNom = rBin.scanLeft(0)(_ + _)
      .drop(1) // drop 1st one which is initial 0
      .zip(rBin)
      .map(t => if (t._2 != 0) t._1.toDouble else 0.0)
    // ( number of hits at this position if hit or 0 if miss )

    val pAtKDenom = 1 to rBin.size
    val pAtK = pAtKNom.zip(pAtKDenom).map { t => t._1 / t._2 }
    val apAtKDenom = scala.math.min(n, r.size)
    if (apAtKDenom == 0) 0 else pAtK.sum / apAtKDenom
  }

}
