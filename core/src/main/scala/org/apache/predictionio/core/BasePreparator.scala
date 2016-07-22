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

/** :: DeveloperApi ::
  * Base class of all preparator controller classes
  *
  * Dev note: Probably will add an extra parameter for ad hoc JSON formatter
  *
  * @tparam TD Training data class
  * @tparam PD Prepared data class
  */
@DeveloperApi
abstract class BasePreparator[TD, PD]
  extends AbstractDoer {
  /** :: DeveloperApi ::
    * Engine developers should not use this directly. This is called by training
    * workflow to prepare data before handing it over to algorithm
    *
    * @param sc Spark context
    * @param td Training data
    * @return Prepared data
    */
  @DeveloperApi
  def prepareBase(sc: SparkContext, td: TD): PD
}
