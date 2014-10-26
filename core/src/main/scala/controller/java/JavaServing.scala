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

import io.prediction.controller.Params
import io.prediction.core.BaseServing
import io.prediction.core.BaseAlgorithm

import scala.collection.JavaConversions._
import scala.reflect._

import java.lang.{ Iterable => JIterable }
import java.util.{ List => JList }

/**
 * Base class of local serving. For deployment, there should only be local
 * serving class.
 *
 * @tparam SP     Serving Parameters
 * @tparam Q      Input Query
 * @tparam P      Output Prediction
 */
abstract class LJavaServing[SP <: Params, Q, P]
  extends BaseServing[SP, Q, P]()(JavaUtils.fakeClassTag[SP]) {

  def serveBase(q: Q, ps: Seq[P]): P = serve(q, seqAsJavaList(ps))

  /**
   * Implement this method to combine multiple algorithms' predictions to
   * produce a single final prediction.
   */
  def serve(query: Q, predictions: JIterable[P]): P
}

/**
 * A concrete implementation of {@link LJavaServing} returning the first
 * algorithm's prediction result directly without any modification.
 *
 * @tparam Q     Input Query
 * @tparam P     Output Prediction
 */
class LJavaFirstServing[Q, P] extends LJavaServing[EmptyParams, Q, P] {
  override def serve(query: Q, predictions: JIterable[P]): P = {
    predictions.iterator().next()
  }
}

/**
 * A concrete implementation of {@link LJavaServing} returning the first
 * algorithm's prediction result directly without any modification.
 */
object LJavaFirstServing {
  /** Returns an instance of {@link LJavaFirstServing}. */
  def apply[Q, P](a: Class[_ <: BaseAlgorithm[_, _, _, Q, P]]) =
    classOf[LJavaFirstServing[Q, P]]

  /**
   * Returns an instance of {@link LJavaFirstServing} by taking a {@link
   * JavaEngineBuilder} as argument.
   */
  def apply[Q, P, B <: JavaEngineBuilder[_, _, _, Q, P, _]](b: B) =
    classOf[LJavaFirstServing[Q, P]]
}
