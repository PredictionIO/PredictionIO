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

package io.prediction.engines.java.olditemrec.algos

// This is a temporary hack. The deployment module requires all components to
// use a single language, in order to make it possible to convert json objects
// into parameter classes. Java classes use GSON library; Scala classes use
// json4s library. Hence, we create some fake parameter classes but name them
// under java package to cheat the deploymenet module.

import io.prediction.controller.Params

class GenericItemBasedParams(
  numRecommendations: Int,
  val itemSimilarity: String,
  val weighted: Boolean) extends MahoutParams(numRecommendations) {

  def this(numRecommendations: Int) =
    this(numRecommendations, GenericItemBased.LOG_LIKELIHOOD, false)
}

class SVDPlusPlusParams(
  numRecommendations: Int,
  val numFeatures: Int,
  val learningRate: Double,
  val preventOverfitting: Double,
  val randomNoise: Double,
  val numIterations: Int,
  val learningRateDecay: Double) extends MahoutParams(numRecommendations) {

  def this(numRecommendations: Int) =
    this(numRecommendations, 3, 0.01, 0.1, 0.01, 3, 1)
}
