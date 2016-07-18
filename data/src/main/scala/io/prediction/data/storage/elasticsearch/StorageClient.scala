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

package io.prediction.data.storage.elasticsearch

import grizzled.slf4j.Logging
import io.prediction.data.storage.BaseStorageClient
import io.prediction.data.storage.StorageClientConfig
import io.prediction.data.storage.StorageClientException
import java.net.InetSocketAddress
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.transport.ConnectTransportException

class StorageClient(val config: StorageClientConfig) extends BaseStorageClient
    with Logging {
  override val prefix = "ES"
  val client = try {
    val hosts = config.properties.get("HOSTS").
      map(_.split(",").toSeq).getOrElse(Seq("localhost"))
    val ports = config.properties.get("PORTS").
      map(_.split(",").toSeq.map(_.toInt)).getOrElse(Seq(9300))
    val settings = Settings.settingsBuilder()
      .put("cluster.name", config.properties.getOrElse("CLUSTERNAME", "elasticsearch"))
    val transportClient = TransportClient.builder().settings(settings).build()
    (hosts zip ports) foreach { hp =>
      transportClient.addTransportAddress(
        new InetSocketTransportAddress(new InetSocketAddress(hp._1, hp._2))
      )
    }
    transportClient
  } catch {
    case e: ConnectTransportException =>
      throw new StorageClientException(e.getMessage, e)
  }
}
