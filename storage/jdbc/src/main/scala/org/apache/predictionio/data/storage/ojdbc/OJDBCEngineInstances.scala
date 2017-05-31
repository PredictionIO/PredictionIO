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


package org.apache.predictionio.data.storage.ojdbc

import grizzled.slf4j.Logging
import org.apache.predictionio.data.storage.EngineInstance
import org.apache.predictionio.data.storage.EngineInstances
import org.apache.predictionio.data.storage.StorageClientConfig
import org.apache.predictionio.data.storage.jdbc.{JDBCEngineInstances, JDBCUtils}
import scalikejdbc._

/** JDBC implementation of [[EngineInstances]] */
class OJDBCEngineInstances(client: String, config: StorageClientConfig, prefix: String)
  extends JDBCEngineInstances(client, config, prefix) {
  override def init() {
    val sql =
      s"""
    create table ${tableName.value} (
      id varchar2(100) not null primary key,
      status varchar2(4096) not null,
      startTime timestamp DEFAULT SYSTIMESTAMP,
      endTime timestamp DEFAULT SYSTIMESTAMP,
      engineId varchar2(4096) not null,
      engineVersion varchar2(4096) not null,
      engineVariant varchar2(4096) not null,
      engineFactory varchar2(4096) not null,
      batch varchar2(4096),
      env varchar2(4096) not null,
      sparkConf varchar2(4096) not null,
      datasourceParams varchar2(4096) not null,
      preparatorParams varchar2(4096) not null,
      algorithmsParams varchar2(4096) not null,
      servingParams varchar2(4096) not null)""".replaceAll("\n", "")

    DB autoCommit { implicit session =>
      SQL(JDBCUtils.ifnotcreate(client, sql)).execute().apply()
    }
  }
}
