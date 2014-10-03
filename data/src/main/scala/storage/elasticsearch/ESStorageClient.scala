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

package io.prediction.data.storage.elasticsearch

import io.prediction.data.storage.BaseStorageClient
import io.prediction.data.storage.StorageClientConfig

import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.node.NodeBuilder.nodeBuilder
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.transport.ConnectTransportException

class ESStorageClient(val config: StorageClientConfig)
  extends BaseStorageClient {

  val client = try {
    val transportClient = new TransportClient()
    (config.hosts zip config.ports) foreach { hp =>
      transportClient.addTransportAddress(
        new InetSocketTransportAddress(hp._1, hp._2))
    }
    transportClient
  } catch {
    case e: ConnectTransportException =>
      throw new Exception(e.getMessage)
  }

  val eventClient = new ESEvents(client, "predictionio_appdata")

  override
  val prefix = "ES"
}
