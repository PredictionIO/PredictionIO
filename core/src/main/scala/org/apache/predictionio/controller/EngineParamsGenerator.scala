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

import scala.language.implicitConversions

/** Defines an engine parameters generator.
  *
  * Implementations of this trait can be supplied to "pio eval" as the second
  * command line argument.
  *
  * @group Evaluation
  */
trait EngineParamsGenerator {
  protected[this] var epList: Seq[EngineParams] = _
  protected[this] var epListSet: Boolean = false

  /** Returns the list of [[EngineParams]] of this [[EngineParamsGenerator]]. */
  def engineParamsList: Seq[EngineParams] = {
    assert(epListSet, "EngineParamsList not set")
    epList
  }

  /** Sets the list of [[EngineParams]] of this [[EngineParamsGenerator]]. */
  def engineParamsList_=(l: Seq[EngineParams]) {
    assert(!epListSet, "EngineParamsList can bet set at most once")
    epList = Seq(l:_*)
    epListSet = true
  }
}
