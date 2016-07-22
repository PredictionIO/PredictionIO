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

package org.apache.predictionio.data.storage.localfs

import java.io.File

import grizzled.slf4j.Logging
import org.apache.predictionio.data.storage.BaseStorageClient
import org.apache.predictionio.data.storage.StorageClientConfig
import org.apache.predictionio.data.storage.StorageClientException

class StorageClient(val config: StorageClientConfig) extends BaseStorageClient
    with Logging {
  override val prefix = "LocalFS"
  val f = new File(
    config.properties.getOrElse("PATH", config.properties("HOSTS")))
  if (f.exists) {
    if (!f.isDirectory) throw new StorageClientException(
      s"${f} already exists but it is not a directory!",
      null)
    if (!f.canWrite) throw new StorageClientException(
      s"${f} already exists but it is not writable!",
      null)
  } else {
    if (!f.mkdirs) throw new StorageClientException(
      s"${f} does not exist and automatic creation failed!",
      null)
  }
  val client = f
}
