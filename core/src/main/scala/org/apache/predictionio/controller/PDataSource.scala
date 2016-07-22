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

import org.apache.predictionio.core.BaseDataSource
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

/** Base class of a parallel data source.
  *
  * A parallel data source runs locally within a single machine, or in parallel
  * on a cluster, to return data that is distributed across a cluster.
  *
  * @tparam TD Training data class.
  * @tparam EI Evaluation Info class.
  * @tparam Q Input query class.
  * @tparam A Actual value class.
  * @group Data Source
  */

abstract class PDataSource[TD, EI, Q, A]
  extends BaseDataSource[TD, EI, Q, A] {

  def readTrainingBase(sc: SparkContext): TD = readTraining(sc)

  /** Implement this method to only return training data from a data source */
  def readTraining(sc: SparkContext): TD

  def readEvalBase(sc: SparkContext): Seq[(TD, EI, RDD[(Q, A)])] = readEval(sc)

  /** To provide evaluation feature for your engine, your must override this
    * method to return data for evaluation from a data source. Returned data can
    * optionally include a sequence of query and actual value pairs for
    * evaluation purpose.
    *
    * The default implementation returns an empty sequence as a stub, so that
    * an engine can be compiled without implementing evaluation.
    */
  def readEval(sc: SparkContext): Seq[(TD, EI, RDD[(Q, A)])] =
    Seq[(TD, EI, RDD[(Q, A)])]()

  @deprecated("Use readEval() instead.", "0.9.0")
  def read(sc: SparkContext): Seq[(TD, EI, RDD[(Q, A)])] = readEval(sc)
}
