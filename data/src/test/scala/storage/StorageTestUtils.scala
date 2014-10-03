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

import io.prediction.data.storage.hbase.HBClient

import com.mongodb.casbah.Imports._
import org.elasticsearch.client.Client

object StorageTestUtils {
  val elasticsearchSourceName = "ELASTICSEARCH"
  val mongodbSourceName = "MONGODB"
  val hbaseSourceName = "hbase"

  def dropElasticsearchIndex(indexName: String) = {
    Storage.getClient(elasticsearchSourceName).get.client.asInstanceOf[Client].
      admin.indices.prepareDelete(indexName).get
  }

  def dropMongoDatabase(dbName: String) = {
    Storage.getClient(mongodbSourceName).get.client.asInstanceOf[MongoClient].
      dropDatabase(dbName)
  }

  def dropHBaseNamespace(namespace: String) = {

    val hbClient = Storage.getClient(mongodbSourceName).get.client
      .asInstanceOf[HBClient]

    val tableNames = hbClient.admin.listTableNamesByNamespace(namespace)
    tableNames.foreach { name =>
      hbClient.admin.disableTable(name)
      hbClient.admin.deleteTable(name)
    }
    hbClient.admin.deleteNamespace(namespace)
  }
}
