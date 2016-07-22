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

package org.apache.predictionio.data.storage

import org.apache.predictionio.data.storage.hbase.HBLEvents
import scalikejdbc._

object StorageTestUtils {
  val hbaseSourceName = "HBASE"
  val jdbcSourceName = "PGSQL"

  def dropHBaseNamespace(namespace: String): Unit = {
    val eventDb = Storage.getDataObject[LEvents](hbaseSourceName, namespace)
      .asInstanceOf[HBLEvents]
    val admin = eventDb.client.admin
    val tableNames = admin.listTableNamesByNamespace(namespace)
    tableNames.foreach { name =>
      admin.disableTable(name)
      admin.deleteTable(name)
    }

    //Only empty namespaces (no tables) can be removed.
    admin.deleteNamespace(namespace)
  }

  def dropJDBCTable(table: String): Unit = DB autoCommit { implicit s =>
    SQL(s"drop table $table").execute().apply()
  }
}
