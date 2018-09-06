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

import org.specs2.Specification

class JDBCUtilsSpec extends Specification {
  def is = s2"""

  PredictionIO JDBC Utilities Specification

  driverType should extract the correct portion from a JDBC URL ${driverType}
  mapToString should return an empty string with empty map input ${mapToStringEmptyInput}
  stringToMap should correctly create mapping ${stringToMap}
  stringToMap should return an empty map with empty string input ${stringToMapEmptyInput}

  """

  def driverType = {
    JDBCUtils.driverType("jdbc:postgresql://remotehost:5432/somedbname") must beEqualTo("postgresql")
  }

  def mapToStringEmptyInput = {
    JDBCUtils.mapToString(Map.empty[String, String]) must be empty
  }

  def stringToMap = {
    val m = JDBCUtils.stringToMap("FOO=BAR,DEAD=BEEF")
    m must havePairs("FOO" -> "BAR", "DEAD" -> "BEEF")
  }

  def stringToMapEmptyInput = {
    JDBCUtils.stringToMap("") must be empty
  }
}
