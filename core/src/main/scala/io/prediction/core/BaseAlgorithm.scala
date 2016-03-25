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

import com.google.gson.TypeAdapterFactory
import io.prediction.annotation.DeveloperApi
import io.prediction.controller.Params
import io.prediction.controller.Utils
import net.jodah.typetools.TypeResolver
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

/** :: DeveloperApi ::
  * Base trait with default custom query serializer, exposed to engine developer
  * via [[io.prediction.controller.CustomQuerySerializer]]
  */
@DeveloperApi
trait BaseQuerySerializer {
  /** :: DeveloperApi ::
    * Serializer for Scala query classes using
    * [[io.prediction.controller.Utils.json4sDefaultFormats]]
    */
  @DeveloperApi
  @transient lazy val querySerializer = Utils.json4sDefaultFormats

  /** :: DeveloperApi ::
    * Serializer for Java query classes using Gson
    */
  @DeveloperApi
  @transient lazy val gsonTypeAdapterFactories = Seq.empty[TypeAdapterFactory]
}

/** :: DeveloperApi ::
  * Base class of all algorithm controllers
  *
  * @tparam PD Prepared data class
  * @tparam M Model class
  * @tparam Q Query class
  * @tparam P Predicted result class
  */
@DeveloperApi
abstract class BaseAlgorithm[PD, M, Q, P]
  extends AbstractDoer with BaseQuerySerializer {
  /** :: DeveloperApi ::
    * Engine developers should not use this directly. This is called by workflow
    * to train a model.
    *
    * @param sc Spark context
    * @param pd Prepared data
    * @return Trained model
    */
  @DeveloperApi
  def trainBase(sc: SparkContext, pd: PD): M

  /** :: DeveloperApi ::
    * Engine developers should not use this directly. This is called by
    * evaluation workflow to perform batch prediction.
    *
    * @param sc Spark context
    * @param bm Model
    * @param qs Batch of queries
    * @return Batch of predicted results
    */
  @DeveloperApi
  def batchPredictBase(sc: SparkContext, bm: Any, qs: RDD[(Long, Q)])
  : RDD[(Long, P)]

  /** :: DeveloperApi ::
    * Engine developers should not use this directly. Called by serving to
    * perform a single prediction.
    *
    * @param bm Model
    * @param q Query
    * @return Predicted result
    */
  @DeveloperApi
  def predictBase(bm: Any, q: Q): P

  /** :: DeveloperApi ::
    * Engine developers should not use this directly. Prepare a model for
    * persistence in the downstream consumer. PredictionIO supports 3 types of
    * model persistence: automatic persistence, manual persistence, and
    * re-training on deployment. This method provides a way for downstream
    * modules to determine which mode the model should be persisted.
    *
    * @param sc Spark context
    * @param modelId Model ID
    * @param algoParams Algorithm parameters that trained this model
    * @param bm Model
    * @return The model itself for automatic persistence, an instance of
    *         [[io.prediction.workflow.PersistentModelManifest]] for manual
    *         persistence, or Unit for re-training on deployment
    */
  @DeveloperApi
  def makePersistentModel(
    sc: SparkContext,
    modelId: String,
    algoParams: Params,
    bm: Any): Any = Unit

  /** :: DeveloperApi ::
    * Obtains the type signature of query for this algorithm
    *
    * @return Type signature of query
    */
  def queryClass: Class[Q] = {
    val types = TypeResolver.resolveRawArguments(classOf[BaseAlgorithm[PD, M, Q, P]], getClass)
    types(2).asInstanceOf[Class[Q]]
  }
}
