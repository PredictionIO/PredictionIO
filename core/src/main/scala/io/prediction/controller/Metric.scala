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

import _root_.io.prediction.controller.java.SerializableComparator
import io.prediction.core.BaseEngine
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.util.StatCounter

import scala.Numeric.Implicits._
import scala.reflect._

/** Base class of a [[Metric]].
  *
  * @tparam EI Evaluation information
  * @tparam Q Query
  * @tparam P Predicted result
  * @tparam A Actual result
  * @tparam R Metric result
  * @group Evaluation
  */
abstract class Metric[EI, Q, P, A, R](implicit rOrder: Ordering[R])
extends Serializable {
  /** Java friendly constructor
    *
    * @param comparator A serializable comparator for sorting the metric results.
    *
    */
  def this(comparator: SerializableComparator[R]) = {
    this()(Ordering.comparatorToOrdering(comparator))
  }

  /** Class name of this [[Metric]]. */
  def header: String = this.getClass.getSimpleName

  /** Calculates the result of this [[Metric]]. */
  def calculate(sc: SparkContext, evalDataSet: Seq[(EI, RDD[(Q, P, A)])]): R

  /** Comparison function for R's ordering. */
  def compare(r0: R, r1: R): Int = rOrder.compare(r0, r1)
}

private [prediction] trait StatsMetricHelper[EI, Q, P, A] {
  def calculate(q: Q, p: P, a: A): Double

  def calculateStats(sc: SparkContext, evalDataSet: Seq[(EI, RDD[(Q, P, A)])])
  : StatCounter = {
    val doubleRDD = sc.union(
      evalDataSet.map { case (_, qpaRDD) =>
        qpaRDD.map { case (q, p, a) => calculate(q, p, a) }
      }
    )

    doubleRDD.stats()
  }
}

private [prediction] trait StatsOptionMetricHelper[EI, Q, P, A] {
  def calculate(q: Q, p: P, a: A): Option[Double]

  def calculateStats(sc: SparkContext, evalDataSet: Seq[(EI, RDD[(Q, P, A)])])
  : StatCounter = {
    val doubleRDD = sc.union(
      evalDataSet.map { case (_, qpaRDD) =>
        qpaRDD.flatMap { case (q, p, a) => calculate(q, p, a) }
      }
    )

    doubleRDD.stats()
  }
}

/** Returns the global average of the score returned by the calculate method.
  *
  * @tparam EI Evaluation information
  * @tparam Q Query
  * @tparam P Predicted result
  * @tparam A Actual result
  *
  * @group Evaluation
  */
abstract class AverageMetric[EI, Q, P, A]
    extends Metric[EI, Q, P, A, Double]
    with StatsMetricHelper[EI, Q, P, A]
    with QPAMetric[Q, P, A, Double] {
  /** Implement this method to return a score that will be used for averaging
    * across all QPA tuples.
    */
  def calculate(q: Q, p: P, a: A): Double

  def calculate(sc: SparkContext, evalDataSet: Seq[(EI, RDD[(Q, P, A)])])
  : Double = {
    calculateStats(sc, evalDataSet).mean
  }
}

/** Returns the global average of the non-None score returned by the calculate
  * method.
  *
  * @tparam EI Evaluation information
  * @tparam Q Query
  * @tparam P Predicted result
  * @tparam A Actual result
  *
  * @group Evaluation
  */
abstract class OptionAverageMetric[EI, Q, P, A]
    extends Metric[EI, Q, P, A, Double]
    with StatsOptionMetricHelper[EI, Q, P, A]
    with QPAMetric[Q, P, A, Option[Double]] {
  /** Implement this method to return a score that will be used for averaging
    * across all QPA tuples.
    */
  def calculate(q: Q, p: P, a: A): Option[Double]

  def calculate(sc: SparkContext, evalDataSet: Seq[(EI, RDD[(Q, P, A)])])
  : Double = {
    calculateStats(sc, evalDataSet).mean
  }
}

/** Returns the global standard deviation of the score returned by the calculate method
  *
  * This method uses org.apache.spark.util.StatCounter library, a one pass
  * method is used for calculation
  *
  * @tparam EI Evaluation information
  * @tparam Q Query
  * @tparam P Predicted result
  * @tparam A Actual result
  *
  * @group Evaluation
  */
abstract class StdevMetric[EI, Q, P, A]
    extends Metric[EI, Q, P, A, Double]
    with StatsMetricHelper[EI, Q, P, A]
    with QPAMetric[Q, P, A, Double] {
  /** Implement this method to return a score that will be used for calculating
    * the stdev
    * across all QPA tuples.
    */
  def calculate(q: Q, p: P, a: A): Double

  def calculate(sc: SparkContext, evalDataSet: Seq[(EI, RDD[(Q, P, A)])])
  : Double = {
    calculateStats(sc, evalDataSet).stdev
  }
}

/** Returns the global standard deviation of the non-None score returned by the calculate method
  *
  * This method uses org.apache.spark.util.StatCounter library, a one pass
  * method is used for calculation
  *
  * @tparam EI Evaluation information
  * @tparam Q Query
  * @tparam P Predicted result
  * @tparam A Actual result
  *
  * @group Evaluation
  */
abstract class OptionStdevMetric[EI, Q, P, A]
    extends Metric[EI, Q, P, A, Double]
    with StatsOptionMetricHelper[EI, Q, P, A]
    with QPAMetric[Q, P, A, Option[Double]] {
  /** Implement this method to return a score that will be used for calculating
    * the stdev
    * across all QPA tuples.
    */
  def calculate(q: Q, p: P, a: A): Option[Double]

  def calculate(sc: SparkContext, evalDataSet: Seq[(EI, RDD[(Q, P, A)])])
  : Double = {
    calculateStats(sc, evalDataSet).stdev
  }
}

/** Returns the sum of the score returned by the calculate method.
  *
  * @tparam EI Evaluation information
  * @tparam Q Query
  * @tparam P Predicted result
  * @tparam A Actual result
  * @tparam R Result, output of the function calculate, must be Numeric
  *
  * @group Evaluation
  */
abstract class SumMetric[EI, Q, P, A, R: ClassTag](implicit num: Numeric[R])
    extends Metric[EI, Q, P, A, R]()(num)
    with QPAMetric[Q, P, A, R] {
  /** Implement this method to return a score that will be used for summing
    * across all QPA tuples.
    */
  def calculate(q: Q, p: P, a: A): R

  def calculate(sc: SparkContext, evalDataSet: Seq[(EI, RDD[(Q, P, A)])])
  : R = {
    val union: RDD[R] = sc.union(
      evalDataSet.map { case (_, qpaRDD) =>
        qpaRDD.map { case (q, p, a) => calculate(q, p, a) }
      }
    )

    union.aggregate[R](num.zero)(_ + _, _ + _)
  }
}

/** Returns zero. Useful as a placeholder during evaluation development when not all components are
  * implemented.
  * @tparam EI Evaluation information
  * @tparam Q Query
  * @tparam P Predicted result
  * @tparam A Actual result
  *
  * @group Evaluation
  */
class ZeroMetric[EI, Q, P, A] extends Metric[EI, Q, P, A, Double]() {
   def calculate(sc: SparkContext, evalDataSet: Seq[(EI, RDD[(Q, P, A)])]): Double = 0.0
}

/** Companion object of [[ZeroMetric]]
  *
  * @group Evaluation
  */
object ZeroMetric {
  /** Returns a ZeroMetric instance using Engine's type parameters. */
  def apply[EI, Q, P, A](engine: BaseEngine[EI, Q, P, A]): ZeroMetric[EI, Q, P, A] = {
    new ZeroMetric[EI, Q, P, A]()
  }
}


/** Trait for metric which returns a score based on Query, PredictedResult,
  * and ActualResult
  *
  * @tparam Q Query class
  * @tparam P Predicted result class
  * @tparam A Actual result class
  * @tparam R Metric result class
  * @group Evaluation
  */
trait QPAMetric[Q, P, A, R] {
  /** Calculate a metric result based on query, predicted result, and actual
    * result
    *
    * @param q Query
    * @param p Predicted result
    * @param a Actual result
    * @return Metric result
    */
  def calculate(q: Q, p: P, a: A): R
}
