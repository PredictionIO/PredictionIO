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

/** Extends a data class with this trait if you want PredictionIO to
  * automatically perform sanity check on your data classes during training.
  * This is very useful when you need to debug your engine.
  *
  * @group Helper
  */
trait SanityCheck {
  /** Implement this method to perform checks on your data. This method should
    * contain assertions that throw exceptions when your data does not meet
    * your pre-defined requirement.
    */
  def sanityCheck(): Unit
}
