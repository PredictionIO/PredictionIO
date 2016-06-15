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

package io.prediction.controller

import io.prediction.core.BaseEngine

import scala.language.implicitConversions

/** Defines a deployment that contains an [[Engine]]
  *
  * @group Engine
  */
trait Deployment extends EngineFactory {
  protected[this] var _engine: BaseEngine[_, _, _, _] = _
  protected[this] var engineSet: Boolean = false

  /** Returns the [[Engine]] of this [[Deployment]] */
  def apply(): BaseEngine[_, _, _, _] = {
    assert(engineSet, "Engine not set")
    _engine
  }

  /** Returns the [[Engine]] contained in this [[Deployment]]. */
  private [prediction]
  def engine: BaseEngine[_, _, _, _] = {
    assert(engineSet, "Engine not set")
    _engine
  }

  /** Sets the [[Engine]] for this [[Deployment]]
    *
    * @param engine An implementation of [[Engine]]
    * @tparam EI Evaluation information class
    * @tparam Q Query class
    * @tparam P Predicted result class
    * @tparam A Actual result class
    */
  def engine_=[EI, Q, P, A](engine: BaseEngine[EI, Q, P, A]) {
    assert(!engineSet, "Engine can be set at most once")
    _engine = engine
    engineSet = true
  }
}
