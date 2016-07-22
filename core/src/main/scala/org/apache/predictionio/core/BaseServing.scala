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
import org.apache.predictionio.annotation.Experimental

/** :: DeveloperApi ::
  * Base class of all serving controller classes
  *
  * @tparam Q Query class
  * @tparam P Predicted result class
  */
@DeveloperApi
abstract class BaseServing[Q, P]
  extends AbstractDoer {
  /** :: Experimental ::
    * Engine developers should not use this directly. This is called by serving
    * layer to supplement process the query before sending it to algorithms.
    *
    * @param q Query
    * @return A supplement Query
    */
  @Experimental
  def supplementBase(q: Q): Q

  /** :: DeveloperApi ::
    * Engine developers should not use this directly. This is called by serving
    * layer to combine multiple predicted results from multiple algorithms, and
    * custom business logic before serving to the end user.
    *
    * @param q Query
    * @param ps List of predicted results
    * @return A single predicted result
    */
  @DeveloperApi
  def serveBase(q: Q, ps: Seq[P]): P
}
