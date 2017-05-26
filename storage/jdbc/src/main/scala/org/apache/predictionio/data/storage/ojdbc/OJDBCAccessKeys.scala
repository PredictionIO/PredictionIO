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
import org.apache.predictionio.data.storage.AccessKey
import org.apache.predictionio.data.storage.AccessKeys
import org.apache.predictionio.data.storage.StorageClientConfig
import org.apache.predictionio.data.storage.jdbc.{JDBCAccessKeys, JDBCUtils}
import scalikejdbc._

import scala.util.Random

/** JDBC implementation of [[AccessKeys]] */
class OJDBCAccessKeys(client: String, config: StorageClientConfig, prefix: String)
  extends JDBCAccessKeys(client, config, prefix) {
  override def init() {

    var createsql =
      s"""
    create table ${tableName.value} (
      accesskey varchar2(64) not null primary key,
      appid integer not null,
      events varchar2(4096))"""

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

    DB autoCommit { implicit session =>
      SQL(ifnotcreate).execute().apply()
    }
  }
}
