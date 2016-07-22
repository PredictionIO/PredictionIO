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

package org.apache.predictionio.core

import org.apache.predictionio.annotation.DeveloperApi
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

/** :: DeveloperApi ::
  * Base class of all data source controllers
  *
  * @tparam TD Training data class
  * @tparam EI Evaluation information class
  * @tparam Q Query class
  * @tparam A Actual result class
  */
@DeveloperApi
abstract class BaseDataSource[TD, EI, Q, A] extends AbstractDoer {
  /** :: DeveloperApi ::
    * Engine developer should not use this directly. This is called by workflow
    * to read training data.
    *
    * @param sc Spark context
    * @return Training data
    */
  @DeveloperApi
  def readTrainingBase(sc: SparkContext): TD

  /** :: DeveloperApi ::
    * Engine developer should not use this directly. This is called by
    * evaluation workflow to read training and validation data.
    *
    * @param sc Spark context
    * @return Sets of training data, evaluation information, queries, and actual
    *         results
    */
  @DeveloperApi
  def readEvalBase(sc: SparkContext): Seq[(TD, EI, RDD[(Q, A)])]
}
