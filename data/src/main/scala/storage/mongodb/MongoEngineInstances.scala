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

package io.prediction.data.storage.mongodb

import io.prediction.data.storage.{ EngineInstance, EngineInstances }

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.conversions.scala._
import com.github.nscala_time.time.Imports._

/** MongoDB implementation of EngineInstances. */
class MongoEngineInstances(client: MongoClient, dbname: String)
  extends EngineInstances {
  private val db = client(dbname)
  private val engineInstanceColl = db("engineInstances")
  private val seq = new MongoSequences(db)

  RegisterJodaTimeConversionHelpers()

  def insert(i: EngineInstance): String = {
    val sn = seq.genNextDaily("engineInstance")
    val now = DateTime.now
    val year = now.year.get
    val month = now.month.get
    val day = now.day.get
    val id = f"$year%04d$month%02d$day%02d$sn%04d"
    val obj = MongoDBObject(
      "_id"                    -> id,
      "status"                 -> i.status,
      "startTime"              -> i.startTime,
      "endTime"                -> i.endTime,
      "engineId"               -> i.engineId,
      "engineVersion"          -> i.engineVersion,
      "engineFactory"          -> i.engineFactory,
      "metricsClass"           -> i.metricsClass,
      "batch"                  -> i.batch,
      "env"                    -> i.env,
      "dataSourceParams"       -> i.dataSourceParams,
      "preparatorParams"       -> i.preparatorParams,
      "algorithmsParams"       -> i.algorithmsParams,
      "servingParams"          -> i.servingParams,
      "metricsParams"          -> i.metricsParams,
      "multipleMetricsResults" -> i.multipleMetricsResults,
      "multipleMetricsResultsHTML" -> i.multipleMetricsResultsHTML,
      "multipleMetricsResultsJSON" -> i.multipleMetricsResultsJSON)
    engineInstanceColl.save(obj)
    id
  }

  def get(id: String): Option[EngineInstance] =
    engineInstanceColl.findOne(MongoDBObject("_id" -> id)) map {
      dbObjToEngineInstance(_)
    }

  def getLatestCompleted(engineId: String, engineVersion: String) = {
    engineInstanceColl.findOne(
      o = MongoDBObject(
        "status" -> "COMPLETED",
        "engineId" -> engineId,
        "engineVersion" -> engineVersion),
      orderBy = MongoDBObject("startTime" -> -1)) map {
        dbObjToEngineInstance(_)
      }
  }

  def getEvalCompleted() = {
    engineInstanceColl.find(MongoDBObject("status" -> "EVALCOMPLETED")).sort(
      MongoDBObject("startTime" -> -1)).map {
        dbObjToEngineInstance(_)
      }.toSeq
  }

  def update(i: EngineInstance): Unit = {
    val obj = MongoDBObject(
      "_id"                    -> i.id,
      "status"                 -> i.status,
      "startTime"              -> i.startTime,
      "endTime"                -> i.endTime,
      "engineId"               -> i.engineId,
      "engineVersion"          -> i.engineVersion,
      "engineFactory"          -> i.engineFactory,
      "metricsClass"           -> i.metricsClass,
      "batch"                  -> i.batch,
      "env"                    -> i.env,
      "dataSourceParams"       -> i.dataSourceParams,
      "preparatorParams"       -> i.preparatorParams,
      "algorithmsParams"       -> i.algorithmsParams,
      "servingParams"          -> i.servingParams,
      "metricsParams"          -> i.metricsParams,
      "multipleMetricsResults" -> i.multipleMetricsResults,
      "multipleMetricsResultsHTML" -> i.multipleMetricsResultsHTML,
      "multipleMetricsResultsJSON" -> i.multipleMetricsResultsJSON)
    engineInstanceColl.save(obj)
  }

  def delete(id: String): Unit = engineInstanceColl.remove(
    MongoDBObject("_id" -> id))

  private def dbObjToEngineInstance(dbObj: DBObject): EngineInstance =
    EngineInstance(
      id = dbObj.as[String]("_id"),
      status = dbObj.as[String]("status"),
      startTime = dbObj.as[DateTime]("startTime"),
      endTime = dbObj.as[DateTime]("endTime"),
      engineId = dbObj.as[String]("engineId"),
      engineVersion = dbObj.as[String]("engineVersion"),
      engineFactory = dbObj.as[String]("engineFactory"),
      metricsClass = dbObj.as[String]("metricsClass"),
      batch = dbObj.as[String]("batch"),
      env = dbObj.as[Map[String, String]]("env"),
      dataSourceParams = dbObj.as[String]("dataSourceParams"),
      preparatorParams = dbObj.as[String]("preparatorParams"),
      algorithmsParams = dbObj.as[String]("algorithmsParams"),
      servingParams = dbObj.as[String]("servingParams"),
      metricsParams = dbObj.as[String]("metricsParams"),
      multipleMetricsResults = dbObj.as[String]("multipleMetricsResults"),
      multipleMetricsResultsHTML =
        dbObj.as[String]("multipleMetricsResultsHTML"),
      multipleMetricsResultsJSON =
        dbObj.as[String]("multipleMetricsResultsJSON"))
}
