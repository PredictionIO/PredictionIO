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

import io.prediction.controller._
import io.prediction.engines.base.{ TrainingData => IRTrainingData }
import io.prediction.engines.base.{ U2IActionTD => IRU2IActionTD }
import io.prediction.engines.itemrank.EventsDataSourceParams
import io.prediction.engines.itemrank.EventsDataSource
import io.prediction.engines.java.olditemrec.data.Query
import io.prediction.engines.java.olditemrec.data.Actual

class DataSourceParams(
  val eventsDataParams: EventsDataSourceParams
  /*
  // TODO(yipjustin): Will add evaluation metrics afterwards.

  // rate >= goal in the testing set
  val goal: Int,
  // perform a X-fold cross-validation, if fold <= 1, training only.
  val fold: Int,
  // use MAP@k
  val k: Int
  */
) extends Params

      //EventsDataSourceParams,

class NewItemRecDataSource(dsp: DataSourceParams)
//class NewItemRecDataSource(dsp: EventsDataSourceParams)
  extends LDataSource[
      DataSourceParams,
      EmptyParams,
      IRTrainingData,
      Query,
      Actual] {

  override def read()
  : Seq[(EmptyParams, IRTrainingData, Seq[(Query, Actual)])] = {
    val irDataSource = new EventsDataSource(dsp.eventsDataParams)
    //val irDataSource = new EventsDataSource(dsp)
    val irTrainingData = irDataSource.readTraining()

    // Will generate testing set. For now, training only.
    Seq((EmptyParams(), irTrainingData, Seq[(Query, Actual)]()))
    /*
    val u2is: Seq[IRU2IActionTD] = irTrainingData.u2iActions

    // Reconstruct TrainingData. We use hash(u2i) mod fold == foldIdx as testing
    // set, and the rest as training set.
    (0 until dsp.fold).map { foldIdx => {
      val testingU2I = u2is.filter(_.hashCode % dsp.fold == foldIdx)
      val trainingU2I = u2is.filterNot(_.hashCode % dsp.fold == foldIdx)

      val trainingData = new IRTrainingData(
        users = irTrainingData.users,
        items = irTrainingData.items,
        u2iActions = trainingU2I)

      val testingData: Seq[(Query, Actual)] = prepareValidation(testingU2I)
    }}
    */
  }
}
