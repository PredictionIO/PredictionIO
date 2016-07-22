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

package org.apache.predictionio.data.storage.elasticsearch

import grizzled.slf4j.Logging
import org.apache.predictionio.data.storage.EngineManifestSerializer
import org.apache.predictionio.data.storage.StorageClientConfig
import org.apache.predictionio.data.storage.EngineManifest
import org.apache.predictionio.data.storage.EngineManifests
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.client.Client
import org.json4s._
import org.json4s.native.Serialization.read
import org.json4s.native.Serialization.write

class ESEngineManifests(client: Client, config: StorageClientConfig, index: String)
  extends EngineManifests with Logging {
  implicit val formats = DefaultFormats + new EngineManifestSerializer
  private val estype = "engine_manifests"
  private def esid(id: String, version: String) = s"$id $version"

  def insert(engineManifest: EngineManifest): Unit = {
    val json = write(engineManifest)
    val response = client.prepareIndex(
      index,
      estype,
      esid(engineManifest.id, engineManifest.version)).
      setSource(json).execute().actionGet()
  }

  def get(id: String, version: String): Option[EngineManifest] = {
    try {
      val response = client.prepareGet(index, estype, esid(id, version)).
        execute().actionGet()
      if (response.isExists) {
        Some(read[EngineManifest](response.getSourceAsString))
      } else {
        None
      }
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        None
    }
  }

  def getAll(): Seq[EngineManifest] = {
    try {
      val builder = client.prepareSearch()
      ESUtils.getAll[EngineManifest](client, builder)
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        Seq()
    }
  }

  def update(engineManifest: EngineManifest, upsert: Boolean = false): Unit =
    insert(engineManifest)

  def delete(id: String, version: String): Unit = {
    try {
      client.prepareDelete(index, estype, esid(id, version)).execute().actionGet()
    } catch {
      case e: ElasticsearchException => error(e.getMessage)
    }
  }
}
