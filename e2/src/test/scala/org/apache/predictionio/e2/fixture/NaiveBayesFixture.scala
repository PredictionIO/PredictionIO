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
package org.apache.predictionio.e2.fixture

import org.apache.predictionio.e2.engine.LabeledPoint

trait NaiveBayesFixture {

  def fruit = {
    new {
      val Banana = "Banana"
      val Orange = "Orange"
      val OtherFruit = "Other Fruit"
      val NotLong = "Not Long"
      val Long = "Long"
      val NotSweet = "Not Sweet"
      val Sweet = "Sweet"
      val NotYellow = "Not Yellow"
      val Yellow = "Yellow"

      val labeledPoints = Seq(
        LabeledPoint(Banana, Array(Long, Sweet, Yellow)),
        LabeledPoint(Banana, Array(Long, Sweet, Yellow)),
        LabeledPoint(Banana, Array(Long, Sweet, Yellow)),
        LabeledPoint(Banana, Array(Long, Sweet, Yellow)),
        LabeledPoint(Banana, Array(NotLong, NotSweet, NotYellow)),
        LabeledPoint(Orange, Array(NotLong, Sweet, NotYellow)),
        LabeledPoint(Orange, Array(NotLong, NotSweet, NotYellow)),
        LabeledPoint(OtherFruit, Array(Long, Sweet, NotYellow)),
        LabeledPoint(OtherFruit, Array(NotLong, Sweet, NotYellow)),
        LabeledPoint(OtherFruit, Array(Long, Sweet, Yellow)),
        LabeledPoint(OtherFruit, Array(NotLong, NotSweet, NotYellow))
      )
    }
  }
}
