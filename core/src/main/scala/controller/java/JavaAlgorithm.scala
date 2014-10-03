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

package io.prediction.controller.java

import io.prediction.controller.LAlgorithm
import io.prediction.controller.Params

import net.jodah.typetools.TypeResolver

/**
 * Base class of a local algorithm.
 *
 * A local algorithm runs locally within a single machine and produces a model
 * that can fit within a single machine.
 *
 * @param <AP> Algorithm Parameters
 * @param <PD> Prepared Data
 * @param <M> Model
 * @param <Q> Input Query
 * @param <P> Output Prediction
 */
abstract class LJavaAlgorithm[AP <: Params, PD, M, Q, P]
  extends LAlgorithm[AP, PD, M, Q, P]()(
    JavaUtils.fakeClassTag[AP],
    JavaUtils.fakeClassTag[M],
    JavaUtils.fakeManifest[Q]) {
  def train(pd: PD): M

  def predict(model: M, query: Q): P

  /** Returns a Class object of Q for internal use. */
  def queryClass(): Class[Q] = {
    val typeArgs = TypeResolver.resolveRawArguments(
      classOf[LJavaAlgorithm[AP, PD, M, Q, P]],
      getClass)
    typeArgs(3).asInstanceOf[Class[Q]]
  }
}
