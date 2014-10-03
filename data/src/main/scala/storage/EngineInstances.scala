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

package io.prediction.data.storage

import com.github.nscala_time.time.Imports._
import org.json4s._
import org.json4s.native.Serialization

/**
 * EngineInstance object.
 *
 * Stores parameters, model, and evaluation results for each engine instance.
 *
 * @param id Engine instance ID.
 * @param status Status of the engine instance.
 * @param startTime Start time of the training/evaluation.
 * @param endTime End time of the training/evaluation.
 * @param engineId Engine ID of the instance.
 * @param engineVersion Engine version of the instance.
 * @param engineFactory Engine factory class for the instance.
 * @param metricsClass Name of metrics class of the evaluation of this instance.
 * @param batch A batch label of the engine instance.
 * @param env The environment in which the instance was created.
 * @param dataSourceParams Data source parameters of the instance.
 * @param preparatorParams Preparator parameters of the instance.
 * @param algorithmsParams Algorithms parameters of the instance.
 * @param servingParams Serving parameters of the instance.
 * @param metricsParams Metrics parameters of the instance.
 * @param multipleMetricsResults Results of metrics on all data sets.
 * @param multipleMetricsResultsHTML HTML results of metrics on all data sets.
 * @param multipleMetricsResultsJSON JSON results of metrics on all data sets.
 */
case class EngineInstance(
  id: String,
  status: String,
  startTime: DateTime,
  endTime: DateTime,
  engineId: String,
  engineVersion: String,
  engineFactory: String,
  metricsClass: String,
  batch: String,
  env: Map[String, String],
  dataSourceParams: String,
  preparatorParams: String,
  algorithmsParams: String,
  servingParams: String,
  metricsParams: String,
  multipleMetricsResults: String,
  multipleMetricsResultsHTML: String,
  multipleMetricsResultsJSON: String)

/**
 * Base trait for implementations that interact with EngineInstances in the
 * backend app data store.
 */
trait EngineInstances {
  /** Insert a new EngineInstance. */
  def insert(i: EngineInstance): String

  /** Get a EngineInstance by ID. */
  def get(id: String): Option[EngineInstance]

  /** Get an instance that has started training the latest and has trained to
    * completion.
    */
  def getLatestCompleted(engineId: String, engineVersion: String):
    Option[EngineInstance]

  /** Get instances that are produced by evaluation and have run to completion,
    * reverse sorted by the start time.
    */
  def getEvalCompleted(): Seq[EngineInstance]

  /** Update a EngineInstance. */
  def update(i: EngineInstance): Unit

  /** Delete a EngineInstance. */
  def delete(id: String): Unit
}

class EngineInstanceSerializer extends CustomSerializer[EngineInstance](
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
          engineFactory = "",
          metricsClass = "",
          batch = "",
          env = Map(),
          dataSourceParams = "",
          preparatorParams = "",
          algorithmsParams = "",
          servingParams = "",
          metricsParams = "",
          multipleMetricsResults = "",
          multipleMetricsResultsHTML = "",
          multipleMetricsResultsJSON = "")
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
          case JField("engineFactory", JString(engineFactory)) =>
            i.copy(engineFactory = engineFactory)
          case JField("metricsClass", JString(metricsClass)) =>
            i.copy(metricsClass = metricsClass)
          case JField("batch", JString(batch)) => i.copy(batch = batch)
          case JField("env", env) =>
            i.copy(env = Extraction.extract[Map[String, String]](env))
          case JField("dataSourceParams", JString(dataSourceParams)) =>
            i.copy(dataSourceParams = dataSourceParams)
          case JField("preparatorParams", JString(preparatorParams)) =>
            i.copy(preparatorParams = preparatorParams)
          case JField("algorithmsParams", JString(algorithmsParams)) =>
            i.copy(algorithmsParams = algorithmsParams)
          case JField("servingParams", JString(servingParams)) =>
            i.copy(servingParams = servingParams)
          case JField("metricsParams", JString(metricsParams)) =>
            i.copy(metricsParams = metricsParams)
          case JField("multipleMetricsResults",
            JString(multipleMetricsResults)) =>
              i.copy(multipleMetricsResults = multipleMetricsResults)
          case JField("multipleMetricsResultsHTML",
            JString(multipleMetricsResultsHTML)) =>
              i.copy(multipleMetricsResultsHTML = multipleMetricsResultsHTML)
          case JField("multipleMetricsResultsJSON",
            JString(multipleMetricsResultsJSON)) =>
              i.copy(multipleMetricsResultsJSON = multipleMetricsResultsJSON)
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
        JField("engineFactory", JString(i.engineFactory)) ::
        JField("metricsClass", JString(i.metricsClass)) ::
        JField("batch", JString(i.batch)) ::
        JField("env", Extraction.decompose(i.env)(DefaultFormats)) ::
        JField("dataSourceParams", JString(i.dataSourceParams)) ::
        JField("preparatorParams", JString(i.preparatorParams)) ::
        JField("algorithmsParams", JString(i.algorithmsParams)) ::
        JField("servingParams", JString(i.servingParams)) ::
        JField("metricsParams", JString(i.metricsParams)) ::
        JField("multipleMetricsResults", JString(i.multipleMetricsResults)) ::
        JField("multipleMetricsResultsHTML",
          JString(i.multipleMetricsResultsHTML)) ::
        JField("multipleMetricsResultsJSON",
          JString(i.multipleMetricsResultsJSON)) ::
        Nil)
  }
))
