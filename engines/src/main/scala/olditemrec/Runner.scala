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

import io.prediction.engines.itemrank.EventsDataSource
import io.prediction.engines.itemrank.EventsDataSourceParams
import io.prediction.engines.base.AttributeNames
import io.prediction.engines.itemrank.PreparatorParams

import io.prediction.controller._

import io.prediction.engines.java.olditemrec.algos.GenericItemBasedParams
import io.prediction.engines.java.olditemrec.algos.SVDPlusPlusParams
//import io.prediction.engines.java.olditemrec.ServingParams

//import io.prediction.engines.olditemrec.Model

object Runner {

  def main(args: Array[String]) {
    //val dsp = new DataSourceParams(
    val dsp =
      EventsDataSourceParams(
        appId = 4,
        itypes = None,
        actions = Set("view", "like", "dislike", "conversion", "rate"),
        startTime = None,
        untilTime = None,
        attributeNames = AttributeNames(
          user = "pio_user",
          item = "pio_item",
          u2iActions = Set("view", "like", "dislike", "conversion", "rate"),
          itypes = "pio_itypes",
          starttime = "pio_starttime",
          endtime = "pio_endtime",
          inactive = "pio_inactive",
          rating = "pio_rate"
        ))
    //)

    val pp = new PreparatorParams(
      actions = Map(
        "view" -> Some(3),
        "like" -> Some(5),
        "conversion" -> Some(4),
        "rate" -> None
      ),
      conflict = "latest"
    )

    val svdPlusPlusParams = new SVDPlusPlusParams(10);
    val genericItemBasedParams = new GenericItemBasedParams(10);

    //val sp = new ServingParams();
    val sp = new EmptyParams();

    /*
    Workflow.run(
      dataSourceClassOpt = Some(classOf[EventsDataSource]),
      dataSourceParams = dsp,
      preparatorClassOpt = Some(classOf[NewItemRecPreparator]),
      preparatorParams = pp,
      params = WorkflowParams(
        verbose = 3,
        batch = "PIO: ItemRec"))
    */

    val engine = ItemRecEngine()
    val engineParams = new EngineParams(
      dataSourceParams = dsp,
      preparatorParams = pp,
      algorithmParamsList = Seq(
        ("genericitembased", genericItemBasedParams)),
      servingParams = sp
    )

    Workflow.runEngine(
      params = WorkflowParams(
        batch = "PIO: ItemRec",
        verbose = 3),
      engine = engine,
      engineParams = engineParams)
  }
}
