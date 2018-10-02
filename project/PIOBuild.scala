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

object PIOBuild {
  val elasticsearchVersion = settingKey[String]("The version of Elasticsearch used for building")
  val hbaseVersion = settingKey[String]("The version of Hbase used for building")
  val json4sVersion = settingKey[String]("The version of JSON4S used for building")
  val sparkVersion = settingKey[String]("The version of Apache Spark used for building")
  val sparkBinaryVersion = settingKey[String]("The binary version of Apache Spark used for building")
  val hadoopVersion = settingKey[String]("The version of Apache Hadoop used for building")
  val akkaVersion = settingKey[String]("The version of Akka used for building")

  val childrenPomExtra = settingKey[scala.xml.NodeSeq]("Extra POM data for children projects")
  val elasticsearchSparkArtifact = settingKey[String]("Name of Elasticsearch-Spark artifact used for building")

  def binaryVersion(versionString: String): String = versionString.split('.').take(2).mkString(".")
  def majorVersion(versionString: String): Int = versionString.split('.')(0).toInt
  def minorVersion(versionString: String): Int = versionString.split('.')(1).toInt

  lazy val printBuildInfo = taskKey[Unit]("Print build information")
}
