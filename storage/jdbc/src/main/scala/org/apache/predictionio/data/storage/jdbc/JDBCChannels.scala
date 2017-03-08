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
import org.apache.predictionio.data.storage.Channel
import org.apache.predictionio.data.storage.Channels
import org.apache.predictionio.data.storage.StorageClientConfig
import scalikejdbc._

/** JDBC implementation of [[Channels]] */
class JDBCChannels(client: String, config: StorageClientConfig, prefix: String)
  extends Channels with Logging {
  /** Database table name for this data access object */
  val tableName = JDBCUtils.prefixTableName(prefix, "channels")
  DB autoCommit { implicit session =>
    sql"""
    create table if not exists $tableName (
      id serial not null primary key,
      name text not null,
      appid integer not null)""".execute().apply()
  }

  def insert(channel: Channel): Option[Int] = DB localTx { implicit session =>
    val q = if (channel.id == 0) {
      sql"INSERT INTO $tableName (name, appid) VALUES(${channel.name}, ${channel.appid})"
    } else {
      sql"INSERT INTO $tableName VALUES(${channel.id}, ${channel.name}, ${channel.appid})"
    }
    Some(q.updateAndReturnGeneratedKey().apply().toInt)
  }

  def get(id: Int): Option[Channel] = DB localTx { implicit session =>
    sql"SELECT id, name, appid FROM $tableName WHERE id = $id".
      map(resultToChannel).single().apply()
  }

  def getByAppid(appid: Int): Seq[Channel] = DB localTx { implicit session =>
    sql"SELECT id, name, appid FROM $tableName WHERE appid = $appid".
      map(resultToChannel).list().apply()
  }

  def delete(id: Int): Unit = DB localTx { implicit session =>
    sql"DELETE FROM $tableName WHERE id = $id".update().apply()
  }

  def resultToChannel(rs: WrappedResultSet): Channel = {
    Channel(
      id = rs.int("id"),
      name = rs.string("name"),
      appid = rs.int("appid"))
  }
}
