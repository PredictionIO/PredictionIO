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
import org.apache.predictionio.data.storage.DataMap
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.LEvents
import org.apache.predictionio.data.storage.StorageClientConfig
import org.apache.predictionio.data.storage.jdbc.{JDBCLEvents, JDBCUtils}
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.json4s.JObject
import org.json4s.native.Serialization.read
import org.json4s.native.Serialization.write
import scalikejdbc._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/** JDBC implementation of [[LEvents]] */
class OJDBCLEvents(
                   client: String,
                   config: StorageClientConfig,
                   namespace: String) extends JDBCLEvents(client, config, namespace) {
  implicit private val formats = org.json4s.DefaultFormats

  override def init(appId: Int, channelId: Option[Int] = None): Boolean = {

    // To use index, it must be varchar less than 255 characters on a VARCHAR column
    val useIndex = config.properties.contains("INDEX") &&
      config.properties("INDEX").equalsIgnoreCase("enabled")

    val tableName = JDBCUtils.eventTableName(namespace, appId, channelId)
    val entityIdIndexName = s"idx_${tableName}_ei"
    val entityTypeIndexName = s"idx_${tableName}_et"
    var createsql =
      s"""
      create table $tableName (
         id varchar2(32) not null primary key,
         event varchar2(255) not null,
         entityType varchar2(255) not null,
         entityId varchar2(255) not null,
         targetEntityType varchar2(4096),
         targetEntityId varchar2(4096),
         properties varchar2(4096),
         eventTime timestamp DEFAULT SYSTIMESTAMP,
         eventTimeZone varchar2(50) not null,
         tags varchar2(4096),
         prId varchar2(4096),
         creationTime timestamp DEFAULT SYSTIMESTAMP,
         creationTimeZone varchar2(50) not null)
	""".replaceAll("\n", "")
    var ifnotcreate =
      s"""
        declare
        error_code NUMBER;
        begin
        EXECUTE IMMEDIATE '$createsql';
        exception
        when others then
          if(SQLCODE = -955) then
		        NULL;
          else
		        RAISE;
          end if;
        end;
          """
    // println(ifnotcreate);
    DB autoCommit { implicit session =>
      if (useIndex) {
        SQL(ifnotcreate).execute().apply()
        // create index
        SQL(s"create index $entityIdIndexName on $tableName (entityId)").execute().apply()
        SQL(s"create index $entityTypeIndexName on $tableName (entityType)").execute().apply()
      } else {
        SQL(ifnotcreate).execute().apply()
      }
      true
    }
  }
}
