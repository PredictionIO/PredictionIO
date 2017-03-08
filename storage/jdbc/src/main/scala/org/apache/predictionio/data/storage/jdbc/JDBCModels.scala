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


package org.apache.predictionio.data.storage.jdbc

import grizzled.slf4j.Logging
import org.apache.predictionio.data.storage.Model
import org.apache.predictionio.data.storage.Models
import org.apache.predictionio.data.storage.StorageClientConfig
import scalikejdbc._

/** JDBC implementation of [[Models]] */
class JDBCModels(client: String, config: StorageClientConfig, prefix: String)
  extends Models with Logging {
  /** Database table name for this data access object */
  val tableName = JDBCUtils.prefixTableName(prefix, "models")

  /** Determines binary column type based on JDBC driver type */
  val binaryColumnType = JDBCUtils.binaryColumnType(client)
  DB autoCommit { implicit session =>
    sql"""
    create table if not exists $tableName (
      id varchar(100) not null primary key,
      models $binaryColumnType not null)""".execute().apply()
  }

  def insert(i: Model): Unit = DB localTx { implicit session =>
    sql"insert into $tableName values(${i.id}, ${i.models})".update().apply()
  }

  def get(id: String): Option[Model] = DB readOnly { implicit session =>
    sql"select id, models from $tableName where id = $id".map { r =>
      Model(id = r.string("id"), models = r.bytes("models"))
    }.single().apply()
  }

  def delete(id: String): Unit = DB localTx { implicit session =>
    sql"delete from $tableName where id = $id".execute().apply()
  }
}
