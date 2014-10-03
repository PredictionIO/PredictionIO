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

package io.prediction.data.storage.localfs

import io.prediction.data.storage.BaseStorageClient
import io.prediction.data.storage.StorageClientConfig
import io.prediction.data.storage.StorageClientException

import grizzled.slf4j.Logging

import java.io.File

class StorageClient(val config: StorageClientConfig) extends BaseStorageClient
    with Logging {
  override val prefix = "LocalFS"
  val f = new File(config.hosts.head)
  if (f.exists) {
    if (!f.isDirectory) throw new StorageClientException(
      s"${f} already exists but it is not a directory!")
    if (!f.canWrite) throw new StorageClientException(
      s"${f} already exists but it is not writable!")
  } else {
    if (!f.mkdirs) throw new StorageClientException(
      s"${f} does not exist and automatic creation failed!")
  }
  val client = f
}
