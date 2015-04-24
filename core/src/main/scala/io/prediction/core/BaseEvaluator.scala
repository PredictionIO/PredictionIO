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

package io.prediction.core

import io.prediction.annotation.Experimental   
import io.prediction.controller.EngineParams
import io.prediction.controller.Evaluation
import io.prediction.workflow.WorkflowParams

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

abstract class BaseEvaluator[EI, Q, P, A, ER <: BaseEvaluatorResult]
  extends AbstractDoer {
  def evaluateBase(
    sc: SparkContext,
    evaluation: Evaluation,
    engineEvalDataSet: Seq[(EngineParams, Seq[(EI, RDD[(Q, P, A)])])],
    params: WorkflowParams): ER
}

trait BaseEvaluatorResult extends Serializable {
  /* A short description of the result. */
  def toOneLiner(): String = ""
  
  /** HTML portion of the rendered evaluator results. */
  def toHTML(): String = ""
  
  /** JSON portion of the rendered evaluator results. */
  def toJSON(): String = ""

  /** :: Experimental ::
    * Indicate if this result is inserted into database. */
  @Experimental
  val noSave: Boolean = false 
}
