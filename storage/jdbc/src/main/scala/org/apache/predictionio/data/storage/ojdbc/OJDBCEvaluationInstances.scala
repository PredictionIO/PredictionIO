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
import org.apache.predictionio.data.storage.EvaluationInstance
import org.apache.predictionio.data.storage.EvaluationInstances
import org.apache.predictionio.data.storage.StorageClientConfig
import org.apache.predictionio.data.storage.jdbc.{JDBCEvaluationInstances, JDBCUtils}
import scalikejdbc._

/** JDBC implementations of [[EvaluationInstances]] */
class OJDBCEvaluationInstances(client: String, config: StorageClientConfig, prefix: String)
  extends JDBCEvaluationInstances(client, config, prefix, false) {
  /** Database table name for this data access object */
  override val tableName = JDBCUtils.prefixTableName(prefix, "evaluationinstances")
  var createsql =
  s"""
    create table ${tableName.value} (
      id varchar2(100) not null primary key,
      status varchar2(4096) not null,
      startTime timestamp DEFAULT SYSTIMESTAMP,
      endTime timestamp DEFAULT SYSTIMESTAMP,
      evaluationClass varchar2(4096) not null,
      engineParamsGeneratorClass varchar2(4096) not null,
      batch varchar2(4096) not null,
      env varchar2(4096) not null,
      sparkConf varchar2(4096) not null,
      evaluatorResults varchar2(4096) not null,
      evaluatorResultsHTML varchar2(4096) not null,
      evaluatorResultsJSON varchar2(4096))""".replaceAll("\n", "")

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
