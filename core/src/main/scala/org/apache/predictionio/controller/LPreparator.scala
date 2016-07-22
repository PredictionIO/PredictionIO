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

import org.apache.predictionio.core.BasePreparator
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import scala.reflect._

/** Base class of a local preparator.
  *
  * A local preparator runs locally within a single machine and produces
  * prepared data that can fit within a single machine.
  *
  * @tparam TD Training data class.
  * @tparam PD Prepared data class.
  * @group Preparator
  */
abstract class LPreparator[TD, PD : ClassTag]
  extends BasePreparator[RDD[TD], RDD[PD]] {

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
