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

// We reuse the sample business logic as in ItemRankPreparator. Will refactor
// this code into a common util package.

import io.prediction.engines.itemrank.PreparatorParams
import io.prediction.engines.itemrank.ItemRankPreparator
import io.prediction.engines.base.{ PreparedData => IRPreparedData }
import io.prediction.engines.base.{ TrainingData => IRTrainingData }

import org.apache.mahout.cf.taste.model.DataModel
import io.prediction.engines.util.MahoutUtil;

import io.prediction.engines.java.olditemrec.data.PreparedData


class NewItemRecPreparator(pp: PreparatorParams)
  extends LPreparator[
      PreparatorParams,
      IRTrainingData,
      PreparedData] {

  val irPreparator = new ItemRankPreparator(pp)

  override def prepare(irTrainingData: IRTrainingData): PreparedData = {
    val irPreparedData: IRPreparedData = irPreparator.prepare(irTrainingData)

    val ratings: Seq[(Int, Int, Float, Long)] = irPreparedData.rating
    .map { r => (r.uindex, r.iindex, r.rating.toFloat, r.t) }

    val dataModel = MahoutUtil.buildDataModel(ratings)
    new PreparedData(dataModel)
  }

}
