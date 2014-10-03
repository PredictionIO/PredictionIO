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

package io.prediction.engines.olditemrec

import io.prediction.controller.IEngineFactory
import io.prediction.controller.Engine

import io.prediction.engines.itemrank.EventsDataSource

//import io.prediction.engines.olditemrec.NewItemRecDataSource
//import io.prediction.engines.olditemrec.NewItemRecPreparator
import io.prediction.engines.java.olditemrec.algos.GenericItemBased
import io.prediction.engines.java.olditemrec.algos.SVDPlusPlus
import io.prediction.engines.java.olditemrec.ItemRecServing


object ItemRecEngine extends IEngineFactory {
  def apply() = {
    new Engine(
      classOf[NewItemRecDataSource],
      classOf[NewItemRecPreparator],
      Map(
        "genericitembased" -> classOf[GenericItemBased],
        "svdplusplus" -> classOf[SVDPlusPlus]),
      classOf[ItemRecServing])
  }
}
