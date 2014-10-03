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

package io.prediction.controller

import io.prediction.core.BaseDataSource

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import scala.reflect._

/** Base class of a local data source.
  *
  * A local data source runs locally within a single machine and return data
  * that can fit within a single machine.
  *
  * @tparam DSP Data source parameters class.
  * @tparam DP Data parameters data class.
  * @tparam TD Training data class.
  * @tparam Q Input query class.
  * @tparam A Actual value class.
  * @group Data Source
  */
abstract class LDataSource[
    DSP <: Params : ClassTag,
    DP : ClassTag,
    TD : ClassTag,
    Q,
    A]
  extends BaseDataSource[DSP, DP, RDD[TD], Q, A] {

  def readBase(sc: SparkContext): Seq[(DP, RDD[TD], RDD[(Q, A)])] = {
    val datasets = sc.parallelize(Array(None)).flatMap(_ => read()).zipWithIndex
    datasets.cache
    val dps = datasets.map(t => t._2 -> t._1._1).collect.toMap
    dps.map { t =>
      val dataset = datasets.filter(_._2 == t._1).map(_._1)
      val dp = t._2
      val td = dataset.map(_._2)
      val qa = dataset.map(_._3).flatMap(identity)
      (dp, td, qa)
    }.toSeq
    /*
    read.map { case (dp, td, qaSeq) => {
      (dp, sc.parallelize(Array(td)), sc.parallelize(qaSeq))
    }}
    */
  }

  /** Implement this method to only return training data from a data source.
    */
  def readTraining(): TD = null.asInstanceOf[TD]

  /** Implement this method to return one set of test data (
    * a sequence of query and actual value pairs) from a data source.
    * Should also implement readTraining to return correponding training data.
    */
  def readTest(): (DP, Seq[(Q, A)]) =
    (null.asInstanceOf[DP], Seq.empty[(Q, A)])

  /** Implement this method to return one or more sets of training data
    * and test data (a sequence of query and actual value pairs) from a
    * data source.
    */
  def read(): Seq[(DP, TD, Seq[(Q, A)])] = {
    val (dp, qa) = readTest()
    Seq((dp, readTraining(), qa))
  }

}


/** Base class of a local sliced data source.
  *
  * A local sliced data source runs locally within a single machine and return a
  * single slice of data that can fit within a single machine.
  *
  * @tparam DSP Data source parameters class.
  * @tparam DP Data parameters data class.
  * @tparam TD Training data class.
  * @tparam Q Input query class.
  * @tparam A Actual value class.
  * @group Data Source
  */
abstract class LSlicedDataSource[
    DSP <: Params : ClassTag,
    DP : ClassTag,
    TD : ClassTag,
    Q,
    A]
  extends LDataSource[DSP, DP, TD, Q, A] {

  override def read(): Seq[(DP, TD, Seq[(Q, A)])] = {
    generateDataParams().map { dp =>
      val slicedData = read(dp)
      (dp, slicedData._1, slicedData._2)
    }
  }

  /** Implement this method to return a sequence of data parameters. Each of
    * these can be supplied to `read(dp)` to obtain a single slice of training
    * data.
    */
  def generateDataParams(): Seq[DP]

  /** Implement this method to return data from a data source with given data
    * parameters. Returned data slice can optionally include a sequence of query
    * and actual value pairs for evaluation purpose.
    *
    * @param dp Data parameters.
    */
  def read(dp: DP): (TD, Seq[(Q, A)])
}

/** Base class of a parallel data source.
  *
  * A parallel data source runs locally within a single machine, or in parallel
  * on a cluster, to return data that is distributed across a cluster.
  *
  * @tparam DSP Data source parameters class.
  * @tparam DP Data parameters data class.
  * @tparam TD Training data class.
  * @tparam Q Input query class.
  * @tparam A Actual value class.
  * @group Data Source
  */
abstract class PDataSource[DSP <: Params : ClassTag, DP, TD, Q, A]
  extends BaseDataSource[DSP, DP, TD, Q, A] {

  def readBase(sc: SparkContext): Seq[(DP, TD, RDD[(Q, A)])] = {
    read(sc).map { case (dp, td, qaRdd) => {
      // TODO(yipjustin). Maybe do a size check on td, to make sure the user
      // doesn't supply a huge TD to the driver program.
      (dp, td, qaRdd)
    }}
  }

  /** Implement this method to return data from a data source. Returned data
    * can optionally include a sequence of query and actual value pairs for
    * evaluation purpose.
    */
  def read(sc: SparkContext): Seq[(DP, TD, RDD[(Q, A)])]
}

/** Base class of a parallel-to-local data source.
  *
  * A parallel-to-local data source reads data from a distributed environment,
  * and return training data that can fit within a single machine for being used
  * by local algorithms.
  *
  * @tparam DSP Data source parameters class.
  * @tparam DP Data parameters data class.
  * @tparam TD Training data class.
  * @tparam Q Input query class.
  * @tparam A Actual value class.
  * @group Data Source
  */
abstract class P2LDataSource[DSP <: Params : ClassTag, DP, TD, Q, A]
  extends BaseDataSource[DSP, DP, RDD[TD], Q, A] {

  def readBase(sc: SparkContext): Seq[(DP, RDD[TD], RDD[(Q, A)])] = {
    read(sc).map { case (dp, tdRdd, qaRdd) => {
      // TODO(yipjustin). The trainingData is used by local algorithm. It should
      // contain exactly one element. Maybe perform a check here.
      (dp, tdRdd, qaRdd)
    }}
  }

  /** Implement this method to return data from a data source. Returned data
    * can optionally include a sequence of query and actual value pairs for
    * evaluation purpose.
    */
  def read(sc: SparkContext): Seq[(DP, RDD[TD], RDD[(Q, A)])]

}
