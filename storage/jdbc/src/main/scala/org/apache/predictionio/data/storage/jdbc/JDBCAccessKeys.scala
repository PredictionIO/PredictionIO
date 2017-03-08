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
import org.apache.predictionio.data.storage.AccessKey
import org.apache.predictionio.data.storage.AccessKeys
import org.apache.predictionio.data.storage.StorageClientConfig
import scalikejdbc._

import scala.util.Random

/** JDBC implementation of [[AccessKeys]] */
class JDBCAccessKeys(client: String, config: StorageClientConfig, prefix: String)
  extends AccessKeys with Logging {
  /** Database table name for this data access object */
  val tableName = JDBCUtils.prefixTableName(prefix, "accesskeys")
  DB autoCommit { implicit session =>
    sql"""
    create table if not exists $tableName (
      accesskey varchar(64) not null primary key,
      appid integer not null,
      events text)""".execute().apply()
  }

  def insert(accessKey: AccessKey): Option[String] = DB localTx { implicit s =>
    val key = if (accessKey.key.isEmpty) generateKey else accessKey.key
    val events = if (accessKey.events.isEmpty) None else Some(accessKey.events.mkString(","))
    sql"""
    insert into $tableName values(
      $key,
      ${accessKey.appid},
      $events)""".update().apply()
    Some(key)
  }

  def get(key: String): Option[AccessKey] = DB readOnly { implicit session =>
    sql"SELECT accesskey, appid, events FROM $tableName WHERE accesskey = $key".
      map(resultToAccessKey).single().apply()
  }

  def getAll(): Seq[AccessKey] = DB readOnly { implicit session =>
    sql"SELECT accesskey, appid, events FROM $tableName".map(resultToAccessKey).list().apply()
  }

  def getByAppid(appid: Int): Seq[AccessKey] = DB readOnly { implicit session =>
    sql"SELECT accesskey, appid, events FROM $tableName WHERE appid = $appid".
      map(resultToAccessKey).list().apply()
  }

  def update(accessKey: AccessKey): Unit = DB localTx { implicit session =>
    val events = if (accessKey.events.isEmpty) None else Some(accessKey.events.mkString(","))
    sql"""
    UPDATE $tableName SET
      appid = ${accessKey.appid},
      events = $events
    WHERE accesskey = ${accessKey.key}""".update().apply()
  }

  def delete(key: String): Unit = DB localTx { implicit session =>
    sql"DELETE FROM $tableName WHERE accesskey = $key".update().apply()
  }

  /** Convert JDBC results to [[AccessKey]] */
  def resultToAccessKey(rs: WrappedResultSet): AccessKey = {
    AccessKey(
      key = rs.string("accesskey"),
      appid = rs.int("appid"),
      events = rs.stringOpt("events").map(_.split(",").toSeq).getOrElse(Nil))
  }
}
