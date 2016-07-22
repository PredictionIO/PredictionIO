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
import org.apache.predictionio.data.storage.StorageClientConfig
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.client.Client
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._

class ESSequences(client: Client, config: StorageClientConfig, index: String) extends Logging {
  implicit val formats = DefaultFormats
  private val estype = "sequences"

  val indices = client.admin.indices
  val indexExistResponse = indices.prepareExists(index).get
  if (!indexExistResponse.isExists) {
    // val settingsJson =
    //   ("number_of_shards" -> 1) ~
    //   ("auto_expand_replicas" -> "0-all")
    indices.prepareCreate(index).get
  }
  val typeExistResponse = indices.prepareTypesExists(index).setTypes(estype).get
  if (!typeExistResponse.isExists) {
    val mappingJson =
      (estype ->
        ("_source" -> ("enabled" -> 0)) ~
        ("_all" -> ("enabled" -> 0)) ~
        ("_type" -> ("index" -> "no")) ~
        ("enabled" -> 0))
    indices.preparePutMapping(index).setType(estype).
      setSource(compact(render(mappingJson))).get
  }

  def genNext(name: String): Int = {
    try {
      val response = client.prepareIndex(index, estype, name).
        setSource(compact(render("n" -> name))).get
      response.getVersion().toInt
    } catch {
      case e: ElasticsearchException =>
        error(e.getMessage)
        0
    }
  }
}
