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

import org.json4s._

/**
 * EngineManifest object.
 *
 * The system should be able to host multiple versions of an engine with the
 * same ID.
 *
 * @param id Unique identifier of an engine.
 * @param version Engine version string.
 * @param name A short and descriptive name for the engine.
 * @param description A long description of the engine.
 * @param files Paths to engine files.
 * @param engineFactory Engine's factory class name.
 */
case class EngineManifest(
  id: String,
  version: String,
  name: String,
  description: Option[String],
  files: Seq[String],
  engineFactory: String)

/** Base trait for implementations that interact with engine manifests in the backend data store. */
trait EngineManifests {
  /** Inserts an engine manifest. */
  def insert(engineManifest: EngineManifest): Unit

  /** Get an engine manifest by its ID. */
  def get(id: String, version: String): Option[EngineManifest]

  /** Get all engine manifest. */
  def getAll(): Seq[EngineManifest]

  /** Updates an engine manifest. */
  def update(engineInfo: EngineManifest, upsert: Boolean = false): Unit

  /** Delete an engine manifest by its ID. */
  def delete(id: String, version: String): Unit
}

class EngineManifestSerializer extends CustomSerializer[EngineManifest](format => (
  {
    case JObject(fields) =>
      val seed = EngineManifest(
        id = "",
        version = "",
        name = "",
        description = None,
        files = Nil,
        engineFactory = "")
      fields.foldLeft(seed) { case (enginemanifest, field) =>
        field match {
          case JField("id", JString(id)) => enginemanifest.copy(id = id)
          case JField("version", JString(version)) =>
            enginemanifest.copy(version = version)
          case JField("name", JString(name)) => enginemanifest.copy(name = name)
          case JField("description", JString(description)) =>
            enginemanifest.copy(description = Some(description))
          case JField("files", JArray(s)) =>
            enginemanifest.copy(files = s.map(t =>
              t match {
                case JString(file) => file
                case _ => ""
              }
            ))
          case JField("engineFactory", JString(engineFactory)) =>
            enginemanifest.copy(engineFactory = engineFactory)
          case _ => enginemanifest
        }
      }
  },
  {
    case enginemanifest: EngineManifest =>
      JObject(
        JField("id", JString(enginemanifest.id)) ::
        JField("version", JString(enginemanifest.version)) ::
        JField("name", JString(enginemanifest.name)) ::
        JField("description",
          enginemanifest.description.map(
            x => JString(x)).getOrElse(JNothing)) ::
        JField("files",
          JArray(enginemanifest.files.map(x => JString(x)).toList)) ::
        JField("engineFactory", JString(enginemanifest.engineFactory)) ::
        Nil)
  }
))
