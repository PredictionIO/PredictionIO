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

package io.prediction.engines.itemrank

import io.prediction.controller.Params
import io.prediction.controller.LAlgorithm

import scala.util.Random

import io.prediction.engines.base.PreparedData

class RandomAlgoParams() extends Params {
  override def toString = s"empty"
}

class RandomModel() extends Serializable {}

class RandomAlgorithm(params: RandomAlgoParams)
  extends LAlgorithm[RandomAlgoParams, PreparedData, RandomModel,
  Query, Prediction] {

  @transient lazy val rand = new Random(3) // TODO: pass seed from init()

  override def train(preparedData: PreparedData): RandomModel = {
    new RandomModel()
  }

  override def predict(model: RandomModel, query: Query): Prediction = {
    val items = query.iids

    new Prediction (
      items = rand.shuffle(items).zip((items.size to 1 by -1).map(_.toDouble))
    )
  }

}
