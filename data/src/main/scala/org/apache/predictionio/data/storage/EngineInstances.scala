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

package org.apache.predictionio.data.storage

import com.github.nscala_time.time.Imports._
import org.apache.predictionio.annotation.DeveloperApi
import org.json4s._

/** :: DeveloperApi ::
  * Stores parameters, model, and other information for each engine instance
  *
  * @param id Engine instance ID.
  * @param status Status of the engine instance.
  * @param startTime Start time of the training/evaluation.
  * @param endTime End time of the training/evaluation.
  * @param engineId Engine ID of the instance.
  * @param engineVersion Engine version of the instance.
  * @param engineVariant Engine variant ID of the instance.
  * @param engineFactory Engine factory class for the instance.
  * @param batch A batch label of the engine instance.
  * @param env The environment in which the instance was created.
  * @param sparkConf Custom Spark configuration of the instance.
  * @param dataSourceParams Data source parameters of the instance.
  * @param preparatorParams Preparator parameters of the instance.
  * @param algorithmsParams Algorithms parameters of the instance.
  * @param servingParams Serving parameters of the instance.
  * @group Meta Data
  */
@DeveloperApi
case class EngineInstance(
  id: String,
  status: String,
  startTime: DateTime,
  endTime: DateTime,
  engineId: String,
  engineVersion: String,
  engineVariant: String,
  engineFactory: String,
  batch: String,
  env: Map[String, String],
  sparkConf: Map[String, String],
  dataSourceParams: String,
  preparatorParams: String,
  algorithmsParams: String,
  servingParams: String)

/** :: DeveloperApi ::
  * Base trait of the [[EngineInstance]] data access object
  *
  * @group Meta Data
  */
@DeveloperApi
trait EngineInstances {
  /** Insert a new [[EngineInstance]] */
  def insert(i: EngineInstance): String

  /** Get an [[EngineInstance]] by ID */
  def get(id: String): Option[EngineInstance]

  /** Get all [[EngineInstance]]s */
  def getAll(): Seq[EngineInstance]

  /** Get an instance that has started training the latest and has trained to
    * completion
    */
  def getLatestCompleted(
      engineId: String,
      engineVersion: String,
      engineVariant: String): Option[EngineInstance]

  /** Get all instances that has trained to completion */
  def getCompleted(
    engineId: String,
    engineVersion: String,
    engineVariant: String): Seq[EngineInstance]

  /** Update an [[EngineInstance]] */
  def update(i: EngineInstance): Unit

  /** Delete an [[EngineInstance]] */
  def delete(id: String): Unit
}

/** :: DeveloperApi ::
  * JSON4S serializer for [[EngineInstance]]
  *
  * @group Meta Data
  */
@DeveloperApi
class EngineInstanceSerializer
    extends CustomSerializer[EngineInstance](
  format => ({
    case JObject(fields) =>
      implicit val formats = DefaultFormats
      val seed = EngineInstance(
          id = "",
          status = "",
          startTime = DateTime.now,
          endTime = DateTime.now,
          engineId = "",
          engineVersion = "",
          engineVariant = "",
          engineFactory = "",
          batch = "",
          env = Map(),
          sparkConf = Map(),
          dataSourceParams = "",
          preparatorParams = "",
          algorithmsParams = "",
          servingParams = "")
      fields.foldLeft(seed) { case (i, field) =>
        field match {
          case JField("id", JString(id)) => i.copy(id = id)
          case JField("status", JString(status)) => i.copy(status = status)
          case JField("startTime", JString(startTime)) =>
            i.copy(startTime = Utils.stringToDateTime(startTime))
          case JField("endTime", JString(endTime)) =>
            i.copy(endTime = Utils.stringToDateTime(endTime))
          case JField("engineId", JString(engineId)) =>
            i.copy(engineId = engineId)
          case JField("engineVersion", JString(engineVersion)) =>
            i.copy(engineVersion = engineVersion)
          case JField("engineVariant", JString(engineVariant)) =>
            i.copy(engineVariant = engineVariant)
          case JField("engineFactory", JString(engineFactory)) =>
            i.copy(engineFactory = engineFactory)
          case JField("batch", JString(batch)) => i.copy(batch = batch)
          case JField("env", env) =>
            i.copy(env = Extraction.extract[Map[String, String]](env))
          case JField("sparkConf", sparkConf) =>
            i.copy(sparkConf = Extraction.extract[Map[String, String]](sparkConf))
          case JField("dataSourceParams", JString(dataSourceParams)) =>
            i.copy(dataSourceParams = dataSourceParams)
          case JField("preparatorParams", JString(preparatorParams)) =>
            i.copy(preparatorParams = preparatorParams)
          case JField("algorithmsParams", JString(algorithmsParams)) =>
            i.copy(algorithmsParams = algorithmsParams)
          case JField("servingParams", JString(servingParams)) =>
            i.copy(servingParams = servingParams)
          case _ => i
        }
      }
  },
  {
    case i: EngineInstance =>
      JObject(
        JField("id", JString(i.id)) ::
        JField("status", JString(i.status)) ::
        JField("startTime", JString(i.startTime.toString)) ::
        JField("endTime", JString(i.endTime.toString)) ::
        JField("engineId", JString(i.engineId)) ::
        JField("engineVersion", JString(i.engineVersion)) ::
        JField("engineVariant", JString(i.engineVariant)) ::
        JField("engineFactory", JString(i.engineFactory)) ::
        JField("batch", JString(i.batch)) ::
        JField("env", Extraction.decompose(i.env)(DefaultFormats)) ::
        JField("sparkConf", Extraction.decompose(i.sparkConf)(DefaultFormats)) ::
        JField("dataSourceParams", JString(i.dataSourceParams)) ::
        JField("preparatorParams", JString(i.preparatorParams)) ::
        JField("algorithmsParams", JString(i.algorithmsParams)) ::
        JField("servingParams", JString(i.servingParams)) ::
        Nil)
  }
))
