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

package org.apache.predictionio.data.storage.jdbc

import grizzled.slf4j.Logging
import org.apache.predictionio.data.storage.EngineManifest
import org.apache.predictionio.data.storage.EngineManifests
import org.apache.predictionio.data.storage.StorageClientConfig
import scalikejdbc._

/** JDBC implementation of [[EngineManifests]] */
class JDBCEngineManifests(client: String, config: StorageClientConfig, prefix: String)
  extends EngineManifests with Logging {
  /** Database table name for this data access object */
  val tableName = JDBCUtils.prefixTableName(prefix, "enginemanifests")
  DB autoCommit { implicit session =>
    sql"""
    create table if not exists $tableName (
      id varchar(100) not null primary key,
      version text not null,
      engineName text not null,
      description text,
      files text not null,
      engineFactory text not null)""".execute().apply()
  }

  def insert(m: EngineManifest): Unit = DB localTx { implicit session =>
    sql"""
    INSERT INTO $tableName VALUES(
      ${m.id},
      ${m.version},
      ${m.name},
      ${m.description},
      ${m.files.mkString(",")},
      ${m.engineFactory})""".update().apply()
  }

  def get(id: String, version: String): Option[EngineManifest] = DB localTx { implicit session =>
    sql"""
    SELECT
      id,
      version,
      engineName,
      description,
      files,
      engineFactory
    FROM $tableName WHERE id = $id AND version = $version""".
      map(resultToEngineManifest).single().apply()
  }

  def getAll(): Seq[EngineManifest] = DB localTx { implicit session =>
    sql"""
    SELECT
      id,
      version,
      engineName,
      description,
      files,
      engineFactory
    FROM $tableName""".map(resultToEngineManifest).list().apply()
  }

  def update(m: EngineManifest, upsert: Boolean = false): Unit = {
    var r = 0
    DB localTx { implicit session =>
      r = sql"""
      update $tableName set
        engineName = ${m.name},
        description = ${m.description},
        files = ${m.files.mkString(",")},
        engineFactory = ${m.engineFactory}
      where id = ${m.id} and version = ${m.version}""".update().apply()
    }
    if (r == 0) {
      if (upsert) {
        insert(m)
      } else {
        error("Cannot find a record to update, and upsert is not enabled.")
      }
    }
  }

  def delete(id: String, version: String): Unit = DB localTx { implicit session =>
    sql"DELETE FROM $tableName WHERE id = $id AND version = $version".
      update().apply()
  }

  /** Convert JDBC results to [[EngineManifest]] */
  def resultToEngineManifest(rs: WrappedResultSet): EngineManifest = {
    EngineManifest(
      id = rs.string("id"),
      version = rs.string("version"),
      name = rs.string("engineName"),
      description = rs.stringOpt("description"),
      files = rs.string("files").split(","),
      engineFactory = rs.string("engineFactory"))
  }
}
