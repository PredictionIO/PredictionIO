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

import grizzled.slf4j.Logging
import org.apache.predictionio.annotation.DeveloperApi
import org.apache.predictionio.controller.Params

/** :: DeveloperApi ::
  * Base class for all controllers
  */
@DeveloperApi
abstract class AbstractDoer extends Serializable

/** :: DeveloperApi ::
  * Provides facility to instantiate controller classes
  */
@DeveloperApi
object Doer extends Logging {
  /** :: DeveloperApi ::
    * Instantiates a controller class using supplied controller parameters as
    * constructor parameters
    *
    * @param cls Class of the controller class
    * @param params Parameters of the controller class
    * @tparam C Controller class
    * @return An instance of the controller class
    */
  @DeveloperApi
  def apply[C <: AbstractDoer] (
    cls: Class[_ <: C], params: Params): C = {

    // Subclasses only allows two kind of constructors.
    // 1. Constructor with P <: Params.
    // 2. Emtpy constructor.
    // First try (1), if failed, try (2).
    try {
      val constr = cls.getConstructor(params.getClass)
      constr.newInstance(params)
    } catch {
      case e: NoSuchMethodException => try {
        val zeroConstr = cls.getConstructor()
        zeroConstr.newInstance()
      } catch {
        case e: NoSuchMethodException =>
          error(s"${params.getClass.getName} was used as the constructor " +
            s"argument to ${e.getMessage}, but no constructor can handle it. " +
            "Aborting.")
          sys.exit(1)
      }
    }
  }
}
