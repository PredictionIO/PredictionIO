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

package org.apache.predictionio.data.storage.jdbc

import grizzled.slf4j.Logging
import org.apache.predictionio.data.storage.BaseStorageClient
import org.apache.predictionio.data.storage.StorageClientConfig
import org.apache.predictionio.data.storage.StorageClientException
import scalikejdbc._

/** JDBC implementation of [[BaseStorageClient]] */
class StorageClient(val config: StorageClientConfig)
  extends BaseStorageClient with Logging {
  override val prefix = "JDBC"

  if (!config.properties.contains("URL")) {
    throw new StorageClientException("The URL variable is not set!", null)
  }
  if (!config.properties.contains("USERNAME")) {
    throw new StorageClientException("The USERNAME variable is not set!", null)
  }
  if (!config.properties.contains("PASSWORD")) {
    throw new StorageClientException("The PASSWORD variable is not set!", null)
  }

  // set max size of connection pool
  val maxSize: Int = config.properties.getOrElse("CONNECTIONS", "8").toInt
  val settings = ConnectionPoolSettings(maxSize = maxSize)

  ConnectionPool.singleton(
    config.properties("URL"),
    config.properties("USERNAME"),
    config.properties("PASSWORD"),
    settings)
  /** JDBC connection URL. Connections are managed by ScalikeJDBC. */
  val client = config.properties("URL")
}
