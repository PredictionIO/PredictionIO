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
import org.apache.predictionio.core.BasePreparator
import org.apache.spark.SparkContext

import scala.reflect._

/** A helper concrete implementation of [[org.apache.predictionio.core.BasePreparator]]
  * that passes training data through without any special preparation. This can
  * be used in place for both [[PPreparator]] and [[LPreparator]].
  *
  * @tparam TD Training data class.
  * @group Preparator
  */
class IdentityPreparator[TD] extends BasePreparator[TD, TD] {
  def prepareBase(sc: SparkContext, td: TD): TD = td
}

/** Companion object of [[IdentityPreparator]] that conveniently returns an
  * instance of the class of [[IdentityPreparator]] for use with
  * [[EngineFactory]].
  *
  * @group Preparator
  */
object IdentityPreparator {
  /** Produces an instance of the class of [[IdentityPreparator]].
    *
    * @param ds Instance of the class of the data source for this preparator.
    */
  def apply[TD](ds: Class[_ <: BaseDataSource[TD, _, _, _]]): Class[IdentityPreparator[TD]] =
    classOf[IdentityPreparator[TD]]
}

/** DEPRECATED. Use [[IdentityPreparator]] instead.
  *
  * @tparam TD Training data class.
  * @group Preparator
  */
@deprecated("Use IdentityPreparator instead.", "0.9.2")
class PIdentityPreparator[TD] extends IdentityPreparator[TD]

/** DEPRECATED. Use [[IdentityPreparator]] instead.
  *
  * @group Preparator
  */
@deprecated("Use IdentityPreparator instead.", "0.9.2")
object PIdentityPreparator {
  /** Produces an instance of the class of [[IdentityPreparator]].
    *
    * @param ds Instance of the class of the data source for this preparator.
    */
  def apply[TD](ds: Class[_ <: BaseDataSource[TD, _, _, _]]): Class[IdentityPreparator[TD]] =
    classOf[IdentityPreparator[TD]]
}

/** DEPRECATED. Use [[IdentityPreparator]] instead.
  *
  * @tparam TD Training data class.
  * @group Preparator
  */
@deprecated("Use IdentityPreparator instead.", "0.9.2")
class LIdentityPreparator[TD] extends IdentityPreparator[TD]

/** DEPRECATED. Use [[IdentityPreparator]] instead.
  *
  * @group Preparator
  */
@deprecated("Use IdentityPreparator instead.", "0.9.2")
object LIdentityPreparator {
  /** Produces an instance of the class of [[IdentityPreparator]].
    *
    * @param ds Instance of the class of the data source for this preparator.
    */
  def apply[TD](ds: Class[_ <: BaseDataSource[TD, _, _, _]]): Class[IdentityPreparator[TD]] =
    classOf[IdentityPreparator[TD]]
}
