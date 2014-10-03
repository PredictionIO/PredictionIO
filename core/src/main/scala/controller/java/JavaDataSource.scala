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

package io.prediction.controller.java

import io.prediction.core.BaseDataSource
import io.prediction.controller.Params

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import java.util.{ List => JList }
import java.util.{ ArrayList => JArrayList }
import java.util.{ Collections => JCollections }
import java.lang.{ Iterable => JIterable }

import scala.collection.JavaConversions._
import scala.reflect._

/**
 * Base class of a local data source.
 *
 * A local data source runs locally within a single machine and return data that
 * can fit within a single machine.
 *
 * @param <DSP> Data Source Parameters
 * @param <DP> Data Parameters
 * @param <TD> Training Data
 * @param <Q> Input Query
 * @param <A> Actual Value
 */
abstract class LJavaDataSource[DSP <: Params, DP, TD, Q, A]
  extends BaseDataSource[DSP, DP, RDD[TD], Q, A]()(
    JavaUtils.fakeClassTag[DSP]) {
  def readBase(sc: SparkContext): Seq[(DP, RDD[TD], RDD[(Q, A)])] = {
    implicit val fakeTdTag: ClassTag[TD] = JavaUtils.fakeClassTag[TD]
    val datasets = sc.parallelize(Array(None)).flatMap(_ => read().toSeq).zipWithIndex
    datasets.cache
    val dps = datasets.map(t => t._2 -> t._1._1).collect.toMap
    dps.map { t =>
      val dataset = datasets.filter(_._2 == t._1).map(_._1)
      val dp = t._2
      val td = dataset.map(_._2)
      val qa = dataset.map(_._3.toSeq).flatMap(identity)
      (dp, td, qa)
    }.toSeq
    /*
    read().toSeq.map(e =>
      (e._1, sc.parallelize(Seq(e._2)), sc.parallelize(e._3.toSeq)))
    */
  }

  /** Implement this method to only return training data from a data source.
    */
  def readTraining(): TD = null.asInstanceOf[TD]

  /** Implement this method to return one set of test data (
    * an Iterable of query and actual value pairs) from a data source.
    * Should also implement readTraining to return correponding training data.
    */
  def readTest(): Tuple2[DP, JIterable[Tuple2[Q, A]]] =
    (null.asInstanceOf[DP], JCollections.emptyList())

  /** Implement this method to return one or more sets of training data
    * and test data (an Iterable of query and actual value pairs) from a
    * data source.
    */
  def read(): JIterable[Tuple3[DP, TD, JIterable[Tuple2[Q, A]]]] = {
    val (dp, qa) = readTest()
    List((dp, readTraining(), qa))
  }

}
