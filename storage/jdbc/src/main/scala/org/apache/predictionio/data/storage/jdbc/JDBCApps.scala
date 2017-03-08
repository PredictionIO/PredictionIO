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
import org.apache.predictionio.data.storage.App
import org.apache.predictionio.data.storage.Apps
import org.apache.predictionio.data.storage.StorageClientConfig
import scalikejdbc._

/** JDBC implementation of [[Apps]] */
class JDBCApps(client: String, config: StorageClientConfig, prefix: String)
  extends Apps with Logging {
  /** Database table name for this data access object */
  val tableName = JDBCUtils.prefixTableName(prefix, "apps")
  DB autoCommit { implicit session =>
    sql"""
    create table if not exists $tableName (
      id serial not null primary key,
      name text not null,
      description text)""".execute.apply()
  }

  def insert(app: App): Option[Int] = DB localTx { implicit session =>
    val q = if (app.id == 0) {
      sql"""
      insert into $tableName (name, description) values(${app.name}, ${app.description})
      """
    } else {
      sql"""
      insert into $tableName values(${app.id}, ${app.name}, ${app.description})
      """
    }
    Some(q.updateAndReturnGeneratedKey().apply().toInt)
  }

  def get(id: Int): Option[App] = DB readOnly { implicit session =>
    sql"SELECT id, name, description FROM $tableName WHERE id = ${id}".map(rs =>
      App(
        id = rs.int("id"),
        name = rs.string("name"),
        description = rs.stringOpt("description"))
    ).single().apply()
  }

  def getByName(name: String): Option[App] = DB readOnly { implicit session =>
    sql"SELECT id, name, description FROM $tableName WHERE name = ${name}".map(rs =>
      App(
        id = rs.int("id"),
        name = rs.string("name"),
        description = rs.stringOpt("description"))
    ).single().apply()
  }

  def getAll(): Seq[App] = DB readOnly { implicit session =>
    sql"SELECT id, name, description FROM $tableName".map(rs =>
      App(
        id = rs.int("id"),
        name = rs.string("name"),
        description = rs.stringOpt("description"))
    ).list().apply()
  }

  def update(app: App): Unit = DB localTx { implicit session =>
    sql"""
    update $tableName set name = ${app.name}, description = ${app.description}
    where id = ${app.id}""".update().apply()
  }

  def delete(id: Int): Unit = DB localTx { implicit session =>
    sql"DELETE FROM $tableName WHERE id = $id".update().apply()
  }
}
