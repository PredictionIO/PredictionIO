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

package org.apache.predictionio.controller

import org.apache.predictionio.workflow.SharedSparkContext

import grizzled.slf4j.Logger
import org.scalatest.Matchers._
import org.scalatest.FunSuite
import org.scalatest.Inside

object MetricDevSuite {
  class QIntSumMetric extends SumMetric[EmptyParams, Int, Int, Int, Int] {
    def calculate(q: Int, p: Int, a: Int): Int = q
  }
  
  class QDoubleSumMetric extends SumMetric[EmptyParams, Int, Int, Int, Double] {
    def calculate(q: Int, p: Int, a: Int): Double = q.toDouble
  }
  
  class QAverageMetric extends AverageMetric[EmptyParams, Int, Int, Int] {
    def calculate(q: Int, p: Int, a: Int): Double = q.toDouble
  }
  
  class QOptionAverageMetric extends OptionAverageMetric[EmptyParams, Int, Int, Int] {
    def calculate(q: Int, p: Int, a: Int): Option[Double] = {
      if (q < 0) { None } else { Some(q.toDouble) }
    }
  }
  
  class QStdevMetric extends StdevMetric[EmptyParams, Int, Int, Int] {
    def calculate(q: Int, p: Int, a: Int): Double = q.toDouble
  }
  
  class QOptionStdevMetric extends OptionStdevMetric[EmptyParams, Int, Int, Int] {
    def calculate(q: Int, p: Int, a: Int): Option[Double] = {
      if (q < 0) { None } else { Some(q.toDouble) }
    }
  }
  
}

class MetricDevSuite
extends FunSuite with Inside with SharedSparkContext {
  @transient lazy val logger = Logger[this.type] 
  
  test("Average Metric") {
    val qpaSeq0 = Seq((1, 0, 0), (2, 0, 0), (3, 0, 0))
    val qpaSeq1 = Seq((4, 0, 0), (5, 0, 0), (6, 0, 0))

    val evalDataSet = Seq(
      (EmptyParams(), sc.parallelize(qpaSeq0)),
      (EmptyParams(), sc.parallelize(qpaSeq1)))
  
    val m = new MetricDevSuite.QAverageMetric()
    val result = m.calculate(sc, evalDataSet)
    
    result shouldBe (21.0 / 6)
  }
  
  test("Option Average Metric") {
    val qpaSeq0 = Seq((1, 0, 0), (2, 0, 0), (3, 0, 0))
    val qpaSeq1 = Seq((-4, 0, 0), (-5, 0, 0), (6, 0, 0))

    val evalDataSet = Seq(
      (EmptyParams(), sc.parallelize(qpaSeq0)),
      (EmptyParams(), sc.parallelize(qpaSeq1)))
  
    val m = new MetricDevSuite.QOptionAverageMetric()
    val result = m.calculate(sc, evalDataSet)
    
    result shouldBe (12.0 / 4)
  }
  
  test("Stdev Metric") {
    val qpaSeq0 = Seq((1, 0, 0), (1, 0, 0), (1, 0, 0), (1, 0, 0))
    val qpaSeq1 = Seq((5, 0, 0), (5, 0, 0), (5, 0, 0), (5, 0, 0))

    val evalDataSet = Seq(
      (EmptyParams(), sc.parallelize(qpaSeq0)),
      (EmptyParams(), sc.parallelize(qpaSeq1)))
  
    val m = new MetricDevSuite.QStdevMetric()
    val result = m.calculate(sc, evalDataSet)
    
    result shouldBe 2.0
  }
  
  test("Option Stdev Metric") {
    val qpaSeq0 = Seq((1, 0, 0), (1, 0, 0), (1, 0, 0), (1, 0, 0))
    val qpaSeq1 = Seq((5, 0, 0), (5, 0, 0), (5, 0, 0), (5, 0, 0), (-5, 0, 0))

    val evalDataSet = Seq(
      (EmptyParams(), sc.parallelize(qpaSeq0)),
      (EmptyParams(), sc.parallelize(qpaSeq1)))
  
    val m = new MetricDevSuite.QOptionStdevMetric()
    val result = m.calculate(sc, evalDataSet)
    
    result shouldBe 2.0
  }

  test("Sum Metric [Int]") {
    val qpaSeq0 = Seq((1, 0, 0), (2, 0, 0), (3, 0, 0))
    val qpaSeq1 = Seq((4, 0, 0), (5, 0, 0), (6, 0, 0))

    val evalDataSet = Seq(
      (EmptyParams(), sc.parallelize(qpaSeq0)),
      (EmptyParams(), sc.parallelize(qpaSeq1)))
  
    val m = new MetricDevSuite.QIntSumMetric()
    val result = m.calculate(sc, evalDataSet)
    
    result shouldBe 21
  }

  test("Sum Metric [Double]") {
    val qpaSeq0 = Seq((1, 0, 0), (2, 0, 0), (3, 0, 0))
    val qpaSeq1 = Seq((4, 0, 0), (5, 0, 0), (6, 0, 0))

    val evalDataSet = Seq(
      (EmptyParams(), sc.parallelize(qpaSeq0)),
      (EmptyParams(), sc.parallelize(qpaSeq1)))
  
    val m = new MetricDevSuite.QDoubleSumMetric()
    val result = m.calculate(sc, evalDataSet)
    
    result shouldBe 21.0
  }
}
