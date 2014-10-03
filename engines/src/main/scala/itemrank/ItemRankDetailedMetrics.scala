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

import io.prediction.controller.Metrics
import io.prediction.controller.Params
import io.prediction.controller.NiceRendering

import com.github.nscala_time.time.Imports._
import scala.math.BigDecimal
import breeze.stats.{ mean, meanAndVariance }

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.{read, write}
import org.json4s.native.Serialization

import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.io.FileInputStream
import java.io.ObjectInputStream

import scala.io.Source
import java.io.PrintWriter
import java.io.File

import io.prediction.engines.util.{ MetricsVisualization => MV }
import scala.util.hashing.MurmurHash3
import scala.collection.immutable.NumericRange
import scala.collection.immutable.Range

trait HasName {
  def name: String
}

case class Stats(
  val average: Double,
  val count: Long,
  val stdev: Double,
  val min: Double,
  val max: Double
) extends Serializable

case class HeatMapData (
  val columns: Array[String],
  val data: Seq[(String, Array[Double])]
) extends Serializable

case class DetailedMetricsData(
  val name: String,
  val measureType: String,
  val algoMean: Double,
  val algoStats: Stats,
  val heatMap: HeatMapData,
  val runs: Seq[(String, Stats, Stats)],  // name, algo, baseline
  val aggregations: Seq[(String, Seq[(String, Stats)])])
  extends Serializable with NiceRendering {

  override def toString(): String = {
    val b = 1.96 * algoStats.stdev / Math.sqrt(algoStats.count)
    val lb = algoStats.average - b
    val ub = algoStats.average + b
    f"$measureType $name ${algoStats.average}%.4f [$lb%.4f, $ub%.4f]"
  }

  def toHTML(): String = html.detailed().toString

  def toJSON(): String = {
    implicit val formats = DefaultFormats
    Serialization.write(this)
  }
}

object MeasureType extends Enumeration {
  type Type = Value
  val MeanAveragePrecisionAtK, PrecisionAtK = Value
}

abstract class ItemRankMeasure extends Serializable {
  def calculate(query: Query, prediction: Prediction, actual: Actual): Double
}

// If k == -1, use query size. Otherwise, use k.
class ItemRankMAP(val k: Int) extends ItemRankMeasure {
  def calculate(query: Query, prediction: Prediction, actual: Actual)
  : Double = {
    val kk = (if (k == -1) query.iids.size else k)

    averagePrecisionAtK(
      kk,
      prediction.items.map(_._1),
      actual.iids.toSet)
  }

  override def toString(): String = s"MAP@$k"

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

class ItemRankPrecision(val k: Int) extends ItemRankMeasure {
  override def toString(): String = s"Precision@$k"

  def calculate(query: Query, prediction: Prediction, actual: Actual)
  : Double = {
    val kk = (if (k == -1) query.iids.size else k)

    val actualItems: Set[String] = actual.iids.toSet

    val relevantCount = prediction.items.take(kk)
      .map(_._1)
      .filter(iid => actualItems(iid))
      .size

    val denominator = Seq(kk, prediction.items.size, actualItems.size).min

    relevantCount.toDouble / denominator
  }
}


// optOutputPath is used for debug purpose. If specified, metrics will output
// the data class to the specified path, and the renderer can generate the html
// independently.
class DetailedMetricsParams(
  val name: String = "",
  val optOutputPath: Option[String] = None,
  val buckets: Int = 10,
  val measureType: MeasureType.Type = MeasureType.MeanAveragePrecisionAtK,
  val measureK: Int = -1
) extends Params {}

class ItemRankDetailedMetrics(params: DetailedMetricsParams)
  extends Metrics[DetailedMetricsParams,
    HasName, Query, Prediction, Actual,
      MetricUnit, Seq[MetricUnit], DetailedMetricsData] {

  val measure: ItemRankMeasure = params.measureType match {
    case MeasureType.MeanAveragePrecisionAtK => {
      new ItemRankMAP(params.measureK)
    }
    case MeasureType.PrecisionAtK => {
      new ItemRankPrecision(params.measureK)
    }
    case _ => {
      throw new NotImplementedError(
        s"MeasureType ${params.measureType} not implemented")
    }
  }

  override def computeUnit(query: Query, prediction: Prediction,
    actual: Actual): MetricUnit  = {

    val k = query.iids.size

    val score = measure.calculate(query, prediction, actual)
    // For calculating baseline, we use the input order of query.
    val baseline = measure.calculate(
      query,
      Prediction(query.iids.map(e => (e, 0.0)), isOriginal = false),
      actual)

    val mu = new MetricUnit(
      q = query,
      p = prediction,
      a = actual,
      score = score,
      baseline = baseline,
      uidHash = MurmurHash3.stringHash(query.uid)
    )

    /*
    if (mu.score > 0.80 && mu.score < 1.00) {
      println()
      println(mu.score)
      println(mu.q)
      println(mu.p)
      println(mu.a)
    }
    */

    mu
  }

  // calcualte MAP at k
  override def computeSet(dataParams: HasName,
    metricUnits: Seq[MetricUnit]): Seq[MetricUnit] = metricUnits

  def calculate(values: Seq[Double]): Stats = {
    val mvc = meanAndVariance(values)
    Stats(mvc.mean, mvc.count, mvc.stdDev, values.min, values.max)
  }

  def calculateResample(
    values: Seq[(Int, Double)]
  ): Stats = {
    // JackKnife resampling.
    val segmentSumCountMap: Map[Int, (Double, Int)] = values
      .map{ case(k, v) => (k % params.buckets, v) }
      .groupBy(_._1)
      .mapValues(l => (l.map(_._2).sum, l.size))

    val sum = values.map(_._2).sum
    val count = values.size

    val segmentMeanMap: Map[Int, Double] = segmentSumCountMap
      .mapValues { case(sSum, sCount) => (sum - sSum) / (count - sCount) }

    val segmentValues = segmentMeanMap.values

    // Assume the mean is normal distributed
    val mvc = meanAndVariance(segmentValues)

    // Double => String
    /*
    val groupByFunc = groupByRange((0.0 until 1.0 by 0.1).toArray, "%.2f")
    val bucketByScoreMap: Map[String, Int] = values
      .map(_._2).map(groupByFunc)
      .groupBy(identity).mapValues(_.size)
    */


    // Stats describes the properties of the buckets
    return Stats(mvc.mean, params.buckets, mvc.stdDev, segmentValues.min, segmentValues.max)
  }

  def aggregateMU(units: Seq[MetricUnit], groupByFunc: MetricUnit => String)
  : Seq[(String, Stats)] =
    aggregate[MetricUnit](units, _.score, groupByFunc)

  def aggregate[T](
    units: Seq[T],
    scoreFunc: T => Double,
    groupByFunc: T => String): Seq[(String, Stats)] = {
    units
      .groupBy(groupByFunc)
      .mapValues(_.map(e => scoreFunc(e)))
      .map{ case(k, l) => (k, calculate(l)) }
      .toSeq
      .sortBy(-_._2.average)
  }

  // return a double to key map based on boundaries.
  def groupByRange(values: Array[Double], format: String = "%f")
  : Double => String = {
    val keys: Array[String] = (0 to values.size).map { i =>
      val s = (if (i == 0) Double.NegativeInfinity else values(i-1))
      val e = (if (i < values.size) values(i) else Double.PositiveInfinity)
      //s"[$s, $e)"
      "[" + format.format(s) + ", " + format.format(e) + ")"
    }.toArray

    def f(v: Double): String = {
      // FIXME. Use binary search or indexWhere
      val i: Option[Int] = (0 until values.size).find(i => v < values(i))
      keys(i.getOrElse(values.size))
    }
    return f
  }

  def computeHeatMap(
    input: Seq[(String, Seq[Double])],
    boundaries: Array[Double],
    format: String = "%.2f") : HeatMapData = {

    val bCount: Int = boundaries.size

    val columns: Array[String] = (0 to bCount).map { i =>
      val s = (if (i == 0) Double.NegativeInfinity else boundaries(i-1))
      val e = (
        if (i < boundaries.size) boundaries(i)
        else Double.PositiveInfinity)
      "[" + format.format(s) + ", " + format.format(e) + ")"
    }.toArray

    val data: Seq[(String, Array[Double])] = input
    .map { case(key, values) => {
      val sum = values.size
      //val distributions: Array[Double] = values
      val distributions: Map[Int, Double] = values
        .map { v =>
          val i = boundaries.indexWhere(b => (v <= b))
          (if (i == -1) bCount else i)
        }
        .groupBy(identity)
        .mapValues(_.size.toDouble)
        .mapValues(v => v / sum)

      (key, (0 to bCount).map { i => distributions.getOrElse(i, 0.0) }.toArray)
    }}

    HeatMapData(columns = columns, data = data)
  }

  override def computeMultipleSets(
    input: Seq[(HasName, Seq[MetricUnit])]): DetailedMetricsData = {
    val allUnits: Seq[MetricUnit] = input.flatMap(_._2)

    val overallStats = (
      "Overall",
      calculateResample(allUnits.map(mu => (mu.uidHash, mu.score))),
      calculateResample(allUnits.map(mu => (mu.uidHash, mu.baseline))))

    val runsStats: Seq[(String, Stats, Stats)] = input
    .map { case(dp, mus) =>
      (dp.name,
        calculateResample(mus.map(mu => (mu.uidHash, mu.score))),
        calculateResample(mus.map(mu => (mu.uidHash, mu.baseline))))
    }
    .sortBy(_._1)

    // Aggregation Stats
    val aggregateByActualSize: Seq[(String, Stats)] = allUnits
      .groupBy(_.a.iids.size)
      .mapValues(_.map(_.score))
      .map{ case(k, l) => (k.toString, calculate(l)) }
      .toSeq
      .sortBy(-_._2.average)

    val scoreAggregation = aggregateMU(
      allUnits,
      mu => groupByRange((0.0 until 1.0 by 0.1).toArray, "%.2f")(mu.score))

    val actionCountAggregation = aggregateMU(
      allUnits,
      mu => groupByRange(Array(0, 1, 3, 10, 30, 100, 300), "%.0f")
        (mu.a.previousActionCount))

    val itemCountAggregation = aggregate[(String, MetricUnit)](
      allUnits.flatMap(mu => mu.a.iids.map(item => (item, mu))),
      _._2.score,
      _._1)

    val dateAggregation = aggregateMU(
      allUnits,
      _.a.localDate.toString)

    val localHourAggregation = aggregateMU(
      allUnits,
      _.a.localDateTime.getHourOfDay.toString)

    val isOriginalAggregation = aggregateMU(allUnits, _.p.isOriginal.toString)

    val avgOrderSizeAggregation = aggregateMU(
      allUnits,
      mu => groupByRange(Array(0, 1, 2, 3, 5, 8, 13), "%.0f")
        (mu.a.averageOrderSize))

    val previousOrdersAggregation = aggregateMU(
      allUnits,
      mu => groupByRange(Array(0, 1, 3, 10, 30, 100), "%.0f")
        (mu.a.previousOrders))

    val varietyAggregation = aggregateMU(
      allUnits,
      mu => groupByRange(Array(0, 1, 2, 3, 5, 8, 13, 21), "%.0f")
        (mu.a.variety))

    val heatMapInput: Seq[(String, Seq[Double])] = input
      .map { case(k, mus) => (k.name, mus.map(_.score)) }

    val heatMap: HeatMapData = computeHeatMap(
      heatMapInput,
      (0.0 until 1.0 by 0.0333).toArray,
      "%.2f")

    val outputData = DetailedMetricsData (
      name = params.name,
      measureType = measure.toString,
      algoMean = overallStats._2.average,
      algoStats = overallStats._2,
      //runs = Seq(overallStats, baselineStats) ++ runsStats,
      //runs = Seq(overallStats, overallResampledStats) ++ runsStats,
      heatMap = heatMap,
      runs = Seq(overallStats) ++ runsStats,
      aggregations = Seq(
        ("ByActualSize", aggregateMU(allUnits, _.a.iids.size.toString)),
        ("ByScore", scoreAggregation),
        ("ByActionCount", actionCountAggregation),
        ("ByFlattenItem", itemCountAggregation),
        ("ByDate", dateAggregation),
        ("ByLocalHour", localHourAggregation),
        ("ByIsOriginal", isOriginalAggregation),
        ("ByAvgOrderSize", avgOrderSizeAggregation),
        ("ByPreviousOrders", previousOrdersAggregation),
        ("ByVariety", varietyAggregation)
      )
    )

    // FIXME: Use param opt path
    params.optOutputPath.map { path =>
      MV.save(outputData, path)
    }

    outputData
  }

  private def printDouble(d: Double): String = {
    BigDecimal(d).setScale(4, BigDecimal.RoundingMode.HALF_UP).toString
  }
}

object ItemRankDetailedMain {
  def main(args: Array[String]) {
    MV.render(MV.load[DetailedMetricsData](args(0)), args(0))
  }
}
