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

import scala.reflect._

/** Base class of a local data source.
  *
  * A local data source runs locally within a single machine and return data
  * that can fit within a single machine.
  *
  * @tparam TD Training data class.
  * @tparam EI Evaluation Info class.
  * @tparam Q Input query class.
  * @tparam A Actual value class.
  * @group Data Source
  */
abstract class LDataSource[TD: ClassTag, EI, Q, A]
  extends BaseDataSource[RDD[TD], EI, Q, A] {

  def readTrainingBase(sc: SparkContext): RDD[TD] = {
    sc.parallelize(Seq(None)).map(_ => readTraining())
  }

  /** Implement this method to only return training data from a data source */
  def readTraining(): TD

  def readEvalBase(sc: SparkContext): Seq[(RDD[TD], EI, RDD[(Q, A)])] = {
    val localEvalData: Seq[(TD, EI, Seq[(Q, A)])] = readEval()

    localEvalData.map { case (td, ei, qaSeq) => {
      val tdRDD = sc.parallelize(Seq(None)).map(_ => td)
      val qaRDD = sc.parallelize(qaSeq)
      (tdRDD, ei, qaRDD)
    }}
  }

  /** To provide evaluation feature for your engine, your must override this
    * method to return data for evaluation from a data source. Returned data can
    * optionally include a sequence of query and actual value pairs for
    * evaluation purpose.
    *
    * The default implementation returns an empty sequence as a stub, so that
    * an engine can be compiled without implementing evaluation.
    */
  def readEval(): Seq[(TD, EI, Seq[(Q, A)])] = Seq[(TD, EI, Seq[(Q, A)])]()

  @deprecated("Use readEval() instead.", "0.9.0")
  def read(): Seq[(TD, EI, Seq[(Q, A)])] = readEval()
}
