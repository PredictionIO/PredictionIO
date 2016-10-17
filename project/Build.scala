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

import sbt._
import Keys._

object PIOBuild extends Build {
  val elasticsearchVersion = SettingKey[String](
    "elasticsearch-version",
    "The version of Elasticsearch used for building.")
  val json4sVersion = SettingKey[String](
    "json4s-version",
    "The version of JSON4S used for building.")
  val sparkVersion = SettingKey[String](
    "spark-version",
    "The version of Apache Spark used for building.")
  val childrenPomExtra = SettingKey[scala.xml.NodeSeq](
    "children-pom-extra",
    "Extra POM data for children projects.")
}
