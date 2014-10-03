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
import io.prediction.core.BasePreparator

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import scala.reflect._
import scala.reflect.runtime.universe._

/** Base class of a local preparator.
  *
  * A local preparator runs locally within a single machine and produces
  * prepared data that can fit within a single machine.
  *
  * @tparam PP Preparator parameters class.
  * @tparam TD Training data class.
  * @tparam PD Prepared data class.
  * @group Preparator
  */
abstract class LPreparator[PP <: Params : ClassTag, TD, PD : ClassTag]
  extends BasePreparator[PP, RDD[TD], RDD[PD]] {

  def prepareBase(sc: SparkContext, rddTd: RDD[TD]): RDD[PD] = {
    rddTd.map(prepare)
  }

  /** Implement this method to produce prepared data that is ready for model
    * training.
    *
    * @param trainingData Training data to be prepared.
    */
  def prepare(trainingData: TD): PD
}

/** Base class of a parallel preparator.
  *
  * A parallel preparator can be run in parallel on a cluster and produces a
  * prepared data that is distributed across a cluster.
  *
  * @tparam PP Preparator parameters class.
  * @tparam TD Training data class.
  * @tparam PD Prepared data class.
  * @group Preparator
  */
abstract class PPreparator[PP <: Params : ClassTag, TD, PD]
  extends BasePreparator[PP, TD, PD] {

  def prepareBase(sc: SparkContext, td: TD): PD = {
    prepare(sc, td)
    // TODO: Optinally check pd size. Shouldn't exceed a few KB.
  }

  /** Implement this method to produce prepared data that is ready for model
    * training.
    *
    * @param sc An Apache Spark context.
    * @param trainingData Training data to be prepared.
    */
  def prepare(sc: SparkContext, trainingData: TD): PD
}

/** A helper concrete implementation of [[io.prediction.core.BasePreparator]]
  * that pass training data through without any special preparation.
  *
  * @group Preparator
  */
class IdentityPreparator[TD] extends BasePreparator[EmptyParams, TD, TD] {
  def prepareBase(sc: SparkContext, td: TD): TD = td
}

/** A helper concrete implementation of [[io.prediction.core.BasePreparator]]
  * that pass training data through without any special preparation.
  *
  * @group Preparator
  */
object IdentityPreparator {
  /** Produces an instance of [[IdentityPreparator]].
    *
    * @param ds Data source.
    */
  def apply[TD](ds: Class[_ <: BaseDataSource[_, _, TD, _, _]]) =
    classOf[IdentityPreparator[TD]]
}
