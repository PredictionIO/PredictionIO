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

import scalikejdbc._

/** JDBC related utilities */
object JDBCUtils {
  /** Extract JDBC driver type from URL
    *
    * @param url JDBC URL
    * @return The driver type, e.g. postgresql
    */
  def driverType(url: String): String = {
    val capture = """jdbc:([^:]+):""".r
    capture findFirstIn url match {
      case Some(capture(driverType)) => driverType
      case None => ""
    }
  }

  /** Determines binary column type from JDBC URL
    *
    * @param url JDBC URL
    * @return Binary column type as SQLSyntax, e.g. LONGBLOB
    */
  def binaryColumnType(url: String): SQLSyntax = {
    driverType(url) match {
      case "postgresql" => sqls"bytea"
      case "mysql" => sqls"longblob"
      case _ => sqls"longblob"
    }
  }

  /** Determines UNIX timestamp conversion function from JDBC URL
    *
    * @param url JDBC URL
    * @return Timestamp conversion function, e.g. TO_TIMESTAMP
    */
  def timestampFunction(url: String): String = {
    driverType(url) match {
      case "postgresql" => "to_timestamp"
      case "mysql" => "from_unixtime"
      case _ => "from_unixtime"
    }
  }

  /** Converts Map of String to String to comma-separated list of key=value
    *
    * @param m Map of String to String
    * @return Comma-separated list, e.g. FOO=BAR,X=Y,...
    */
  def mapToString(m: Map[String, String]): String = {
    m.map(t => s"${t._1}=${t._2}").mkString(",")
  }

  /** Inverse of mapToString
    *
    * @param str Comma-separated list, e.g. FOO=BAR,X=Y,...
    * @return Map of String to String, e.g. Map("FOO" -> "BAR", "X" -> "Y", ...)
    */
  def stringToMap(str: String): Map[String, String] = {
    str.split(",").map { x =>
      val y = x.split("=")
      y(0) -> y(1)
    }.toMap[String, String]
  }

  /** Generate 32-character random ID using UUID with - stripped */
  def generateId: String = java.util.UUID.randomUUID().toString.replace("-", "")

  /** Prefix a table name
    *
    * @param prefix Table prefix
    * @param table Table name
    * @return Prefixed table name
    */
  def prefixTableName(prefix: String, table: String): SQLSyntax =
    sqls.createUnsafely(s"${prefix}_$table")

  /** Derive event table name
    *
    * @param namespace Namespace of event tables
    * @param appId App ID
    * @param channelId Optional channel ID
    * @return Full event table name
    */
  def eventTableName(namespace: String, appId: Int, channelId: Option[Int]): String =
    s"${namespace}_${appId}${channelId.map("_" + _).getOrElse("")}"
}
