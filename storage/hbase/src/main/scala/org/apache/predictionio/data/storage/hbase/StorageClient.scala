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


package org.apache.predictionio.data.storage.hbase

import org.apache.predictionio.data.storage.BaseStorageClient
import org.apache.predictionio.data.storage.StorageClientConfig

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.MasterNotRunningException
import org.apache.hadoop.hbase.ZooKeeperConnectionException
import org.apache.hadoop.hbase.client.HConnectionManager
import org.apache.hadoop.hbase.client.HConnection
import org.apache.hadoop.hbase.client.HBaseAdmin

import grizzled.slf4j.Logging

case class HBClient(
  val conf: Configuration,
  val connection: HConnection,
  val admin: HBaseAdmin
)

class StorageClient(val config: StorageClientConfig)
  extends BaseStorageClient with Logging {

  val conf = HBaseConfiguration.create()

  if (config.test) {
    // use fewer retries and shorter timeout for test mode
    conf.set("hbase.client.retries.number", "1")
    conf.set("zookeeper.session.timeout", "30000");
    conf.set("zookeeper.recovery.retry", "1")
  }

  try {
    HBaseAdmin.checkHBaseAvailable(conf)
  } catch {
    case e: MasterNotRunningException =>
      error("HBase master is not running (ZooKeeper ensemble: " +
        conf.get("hbase.zookeeper.quorum") + "). Please make sure that HBase " +
        "is running properly, and that the configuration is pointing at the " +
        "correct ZooKeeper ensemble.")
      throw e
    case e: ZooKeeperConnectionException =>
      error("Cannot connect to ZooKeeper (ZooKeeper ensemble: " +
        conf.get("hbase.zookeeper.quorum") + "). Please make sure that the " +
        "configuration is pointing at the correct ZooKeeper ensemble. By " +
        "default, HBase manages its own ZooKeeper, so if you have not " +
        "configured HBase to use an external ZooKeeper, that means your " +
        "HBase is not started or configured properly.")
      throw e
    case e: Exception => {
      error("Failed to connect to HBase." +
        " Please check if HBase is running properly.")
      throw e
    }
  }

  val connection = HConnectionManager.createConnection(conf)

  val client = HBClient(
    conf = conf,
    connection = connection,
    admin = new HBaseAdmin(connection)
  )

  override
  val prefix = "HB"
}
