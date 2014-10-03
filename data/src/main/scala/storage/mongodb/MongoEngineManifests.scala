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

import com.mongodb.casbah.Imports._

import io.prediction.data.storage.{ EngineManifest, EngineManifests }

/** MongoDB implementation of EngineManifests. */
class MongoEngineManifests(client: MongoClient, dbname: String)
  extends EngineManifests {
  private val db = client(dbname)
  private val coll = db("engineManifests")

  private def dbObjToEngineManifest(dbObj: DBObject) = EngineManifest(
    id = dbObj.as[String]("_id"),
    version = dbObj.as[String]("version"),
    name = dbObj.as[String]("name"),
    description = dbObj.getAs[String]("description"),
    files = dbObj.as[Seq[String]]("files"),
    engineFactory = dbObj.as[String]("engineFactory"))

  def insert(engineManifest: EngineManifest) = {
    // required fields
    val obj = MongoDBObject(
      "_id" -> engineManifest.id,
      "version" -> engineManifest.version,
      "name" -> engineManifest.name,
      "files" -> engineManifest.files,
      "engineFactory" -> engineManifest.engineFactory)

    // optional fields
    val optObj = engineManifest.description.map { d =>
      MongoDBObject("description" -> d) } getOrElse MongoUtils.emptyObj

    coll.insert(obj ++ optObj)
  }

  def get(id: String, version: String) = coll.findOne(
    MongoDBObject("_id" -> id, "version" -> version)) map {
    dbObjToEngineManifest(_) }

  def getAll() = coll.find().toSeq map { dbObjToEngineManifest(_) }

  def update(engineManifest: EngineManifest, upsert: Boolean = false) = {
    val idObj = MongoDBObject("_id" -> engineManifest.id)
    val requiredObj = MongoDBObject(
      "version" -> engineManifest.version,
      "name" -> engineManifest.name,
      "files" -> engineManifest.files,
      "engineFactory" -> engineManifest.engineFactory)
    val descriptionObj = engineManifest.description.map { d =>
      MongoDBObject("description" -> d) } getOrElse MongoUtils.emptyObj

    coll.update(idObj, idObj ++ requiredObj ++ descriptionObj, upsert)
  }

  def delete(id: String, version: String) =
    coll.remove(MongoDBObject("_id" -> id, "version" -> version))
}
