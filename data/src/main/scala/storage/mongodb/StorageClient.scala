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
import grizzled.slf4j.Logging

import io.prediction.data.storage.BaseStorageClient
import io.prediction.data.storage.StorageClientConfig
import io.prediction.data.storage.StorageClientException

class StorageClient(val config: StorageClientConfig) extends BaseStorageClient
    with Logging {
  override val prefix = "Mongo"
  val client = try {
    val addresses = (config.hosts zip config.ports).map(hp =>
      new ServerAddress(hp._1, hp._2)
    ).toList
    MongoClient(addresses)
  } catch {
    case e: MongoException =>
      throw new StorageClientException(e.getMessage)
  }
}
