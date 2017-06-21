/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.predictionio.data.storage.elasticsearch

import org.apache.predictionio.data.storage.{App, Apps, Storage, StorageClientConfig}
import org.elasticsearch.client.{RestClient, Response}
import scala.collection.JavaConverters._

import org.specs2._
import org.specs2.specification.Step

class ElasticsearchStorageClientSpec extends Specification {
  def is = s2"""

  PredictionIO Storage Elasticsearch REST Client Specification ${getESClient}

  """

  def getESClient = sequential ^ s2"""

    StorageClient should
    - initialize metadata store ${initMetadataStore(appsDO)}

  """

  def initMetadataStore(appsDO: Apps) = sequential ^ s2"""

    creates an app ${createsApp(appsDO)}
    gets apps ${getApps(appsDO)}

  """

  val indexName = "test_pio_storage_meta_" + hashCode

  def appsDO: Apps = Storage.getDataObject[Apps](StorageTestUtils.elasticsearchSourceName, indexName)

  def createsApp(appsDO: Apps) = {
    val newId: Int = 123
    val newApp: App = App(newId, "test1", Some("App for ElasticsearchStorageClientSpec"))
    val id: Option[Int] = appsDO.insert(newApp)
    val createdApp: Option[App] = appsDO.get(id.get)
    createdApp.get.id mustEqual newId
  }

  def getApps(appsDO: Apps) = {
    val apps: Seq[App] = appsDO.getAll()
    println(s"Storage.config ${Storage.config}")
    println(s"getApps ${apps}")
    apps must beAnInstanceOf[Seq[App]]
  }
}