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
import org.apache.predictionio.data.storage.EngineInstance
import org.apache.predictionio.data.storage.EngineInstances
import org.apache.predictionio.data.storage.StorageClientConfig
import scalikejdbc._

/** JDBC implementation of [[EngineInstances]] */
class JDBCEngineInstances(client: String, config: StorageClientConfig, prefix: String)
  extends EngineInstances with Logging {
  /** Database table name for this data access object */
  val tableName = JDBCUtils.prefixTableName(prefix, "engineinstances")
  DB autoCommit { implicit session =>
    sql"""
    create table if not exists $tableName (
      id varchar(100) not null primary key,
      status text not null,
      startTime timestamp DEFAULT CURRENT_TIMESTAMP,
      endTime timestamp DEFAULT CURRENT_TIMESTAMP,
      engineId text not null,
      engineVersion text not null,
      engineVariant text not null,
      engineFactory text not null,
      batch text not null,
      env text not null,
      sparkConf text not null,
      datasourceParams text not null,
      preparatorParams text not null,
      algorithmsParams text not null,
      servingParams text not null)""".execute().apply()
  }

  def insert(i: EngineInstance): String = DB localTx { implicit session =>
    val id = java.util.UUID.randomUUID().toString
    sql"""
    INSERT INTO $tableName VALUES(
      $id,
      ${i.status},
      ${i.startTime},
      ${i.endTime},
      ${i.engineId},
      ${i.engineVersion},
      ${i.engineVariant},
      ${i.engineFactory},
      ${i.batch},
      ${JDBCUtils.mapToString(i.env)},
      ${JDBCUtils.mapToString(i.sparkConf)},
      ${i.dataSourceParams},
      ${i.preparatorParams},
      ${i.algorithmsParams},
      ${i.servingParams})""".update().apply()
    id
  }

  def get(id: String): Option[EngineInstance] = DB localTx { implicit session =>
    sql"""
    SELECT
      id,
      status,
      startTime,
      endTime,
      engineId,
      engineVersion,
      engineVariant,
      engineFactory,
      batch,
      env,
      sparkConf,
      datasourceParams,
      preparatorParams,
      algorithmsParams,
      servingParams
    FROM $tableName WHERE id = $id""".map(resultToEngineInstance).
      single().apply()
  }

  def getAll(): Seq[EngineInstance] = DB localTx { implicit session =>
    sql"""
    SELECT
      id,
      status,
      startTime,
      endTime,
      engineId,
      engineVersion,
      engineVariant,
      engineFactory,
      batch,
      env,
      sparkConf,
      datasourceParams,
      preparatorParams,
      algorithmsParams,
      servingParams
    FROM $tableName""".map(resultToEngineInstance).list().apply()
  }

  def getLatestCompleted(
    engineId: String,
    engineVersion: String,
    engineVariant: String): Option[EngineInstance] =
    getCompleted(engineId, engineVersion, engineVariant).headOption

  def getCompleted(
    engineId: String,
    engineVersion: String,
    engineVariant: String): Seq[EngineInstance] = DB localTx { implicit s =>
    sql"""
    SELECT
      id,
      status,
      startTime,
      endTime,
      engineId,
      engineVersion,
      engineVariant,
      engineFactory,
      batch,
      env,
      sparkConf,
      datasourceParams,
      preparatorParams,
      algorithmsParams,
      servingParams
    FROM $tableName
    WHERE
      status = 'COMPLETED' AND
      engineId = $engineId AND
      engineVersion = $engineVersion AND
      engineVariant = $engineVariant
    ORDER BY startTime DESC""".
      map(resultToEngineInstance).list().apply()
  }

  def update(i: EngineInstance): Unit = DB localTx { implicit session =>
    sql"""
    update $tableName set
      status = ${i.status},
      startTime = ${i.startTime},
      endTime = ${i.endTime},
      engineId = ${i.engineId},
      engineVersion = ${i.engineVersion},
      engineVariant = ${i.engineVariant},
      engineFactory = ${i.engineFactory},
      batch = ${i.batch},
      env = ${JDBCUtils.mapToString(i.env)},
      sparkConf = ${JDBCUtils.mapToString(i.sparkConf)},
      datasourceParams = ${i.dataSourceParams},
      preparatorParams = ${i.preparatorParams},
      algorithmsParams = ${i.algorithmsParams},
      servingParams = ${i.servingParams}
    where id = ${i.id}""".update().apply()
  }

  def delete(id: String): Unit = DB localTx { implicit session =>
    sql"DELETE FROM $tableName WHERE id = $id".update().apply()
  }

  /** Convert JDBC results to [[EngineInstance]] */
  def resultToEngineInstance(rs: WrappedResultSet): EngineInstance = {
    EngineInstance(
      id = rs.string("id"),
      status = rs.string("status"),
      startTime = rs.jodaDateTime("startTime"),
      endTime = rs.jodaDateTime("endTime"),
      engineId = rs.string("engineId"),
      engineVersion = rs.string("engineVersion"),
      engineVariant = rs.string("engineVariant"),
      engineFactory = rs.string("engineFactory"),
      batch = rs.string("batch"),
      env = JDBCUtils.stringToMap(rs.string("env")),
      sparkConf = JDBCUtils.stringToMap(rs.string("sparkConf")),
      dataSourceParams = rs.string("datasourceParams"),
      preparatorParams = rs.string("preparatorParams"),
      algorithmsParams = rs.string("algorithmsParams"),
      servingParams = rs.string("servingParams"))
  }
}
