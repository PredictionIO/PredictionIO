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

case class Profile(
  name: String,
  scalaVersion: String,
  sparkVersion: String,
  hadoopVersion: String,
  akkaVersion: String,
  elasticsearchVersion: String)

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
  val hadoopVersion = SettingKey[String](
    "hadoop-version",
    "The version of Apache Hadoop used for building")
  val akkaVersion = SettingKey[String](
    "akka-version",
    "The version of Akka used for building")
  val buildProfile = SettingKey[Profile](
    "build-profile",
    "The dependency profile used for the build")

  def versionPrefix(versionString: String) =
    versionString.split('.').take(2).mkString(".")

  def versionMajor(versionString: String) = versionString.split('.')(0).toInt
  def versionMinor(versionString: String) = versionString.split('.')(1).toInt

  lazy val printProfile = taskKey[Unit]("Print settings for the chosen profile")
}
