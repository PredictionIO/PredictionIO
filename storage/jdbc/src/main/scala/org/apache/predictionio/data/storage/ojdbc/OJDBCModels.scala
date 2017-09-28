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

import java.io.{ByteArrayInputStream, InputStream}
import java.sql.PreparedStatement

import grizzled.slf4j.Logging
import org.apache.predictionio.data.storage.Model
import org.apache.predictionio.data.storage.Models
import org.apache.predictionio.data.storage.StorageClientConfig
import org.apache.predictionio.data.storage.jdbc.{JDBCModels, JDBCUtils}
import scalikejdbc._

/** JDBC implementation of [[Models]] */
class OJDBCModels(client: String, config: StorageClientConfig, prefix: String)
  extends JDBCModels(client, config, prefix) {
  override def init() = {
    /** Determines binary column type based on JDBC driver type */
    val binaryColumnType = JDBCUtils.binaryColumnType(client)
    val sql =
      s"""
    create table ${tableName.value} (
      id varchar2(100) not null primary key,
      models ${binaryColumnType.value} not null)
    """.replaceAll("\n", "")

    DB autoCommit { implicit session =>
      SQL(JDBCUtils.ifnotcreate(client, sql)).execute().apply()
    }
  }

  override def insert(i: Model): Unit = DB localTx { implicit session =>
    val bytesBinder = ParameterBinder[InputStream](
      value = new ByteArrayInputStream(i.models),
      binder = _.setBinaryStream(_, new ByteArrayInputStream(i.models), i.models.length)
    )
    sql"insert into $tableName values(${i.id}, ${bytesBinder})".update().apply()
  }
}
