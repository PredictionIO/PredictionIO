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
import scalikejdbc._

/** JDBC implementation of [[Models]] */
class OJDBCModels(client: String, config: StorageClientConfig, prefix: String)
  extends Models with Logging {
  /** Database table name for this data access object */
  val tableName = OJDBCUtils.prefixTableName(prefix, "models")


  /** Determines binary column type based on JDBC driver type */
  val binaryColumnType = OJDBCUtils.binaryColumnType(client).value


  var createsql =
    s"""
    create table ${tableName.value} (
      id varchar2(100) not null primary key,
      models $binaryColumnType not null)
    """.replaceAll("\n", "")

  // println(createsql)
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
  // println(ifnotcreate)
  DB autoCommit { implicit session =>
    SQL(ifnotcreate).execute().apply()
  }

  def insert(i: Model): Unit = DB localTx { implicit session =>
    val bytesBinder = ParameterBinder[InputStream](
      value = new ByteArrayInputStream(i.models),
      binder = _.setBinaryStream(_, new ByteArrayInputStream(i.models), i.models.length)
    )
    sql"insert into $tableName values(${i.id}, ${bytesBinder})".update().apply()
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
