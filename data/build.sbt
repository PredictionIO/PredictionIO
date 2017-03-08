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

name := "apache-predictionio-data"

libraryDependencies ++= Seq(
  "com.github.nscala-time" %% "nscala-time"    % "2.6.0",
  "commons-codec"           % "commons-codec"  % "1.9",
  "io.spray"               %% "spray-can"      % "1.3.3",
  "io.spray"               %% "spray-routing"  % "1.3.3",
  "io.spray"               %% "spray-testkit"  % "1.3.3" % "test",
  "mysql"                   % "mysql-connector-java" % "5.1.37" % "optional",
  "org.apache.hadoop"       % "hadoop-common"  % "2.6.2"
    exclude("javax.servlet", "servlet-api"),
  "org.apache.hbase"        % "hbase-common"   % "0.98.5-hadoop2",
  "org.apache.hbase"        % "hbase-client"   % "0.98.5-hadoop2"
    exclude("org.apache.zookeeper", "zookeeper"),
  // added for Parallel storage interface
  "org.apache.hbase"        % "hbase-server"   % "0.98.5-hadoop2"
    exclude("org.apache.hbase", "hbase-client")
    exclude("org.apache.zookeeper", "zookeeper")
    exclude("javax.servlet", "servlet-api")
    exclude("org.mortbay.jetty", "servlet-api-2.5")
    exclude("org.mortbay.jetty", "jsp-api-2.1")
    exclude("org.mortbay.jetty", "jsp-2.1"),
  "org.apache.zookeeper"    % "zookeeper"      % "3.4.7"
    exclude("org.slf4j", "slf4j-api")
    exclude("org.slf4j", "slf4j-log4j12"),
  "org.apache.spark"       %% "spark-core"     % sparkVersion.value % "provided",
  "org.apache.spark"       %% "spark-sql"      % sparkVersion.value % "provided",
  "org.clapper"            %% "grizzled-slf4j" % "1.0.2",
  "org.elasticsearch"       % "elasticsearch"  % elasticsearchVersion.value,
//  "org.elasticsearch" % "elasticsearch-spark-13_2.10" % "5.0.0",
  "org.elasticsearch" % "elasticsearch-spark_2.10" % "2.4.3" % "provided",
  "org.elasticsearch" % "elasticsearch-hadoop-mr" % "2.4.3",
  "org.json4s"             %% "json4s-native"  % json4sVersion.value,
  "org.json4s"             %% "json4s-ext"     % json4sVersion.value,
  "org.postgresql"          % "postgresql"     % "9.4-1204-jdbc41",
  "org.scalatest"          %% "scalatest"      % "2.1.7" % "test",
  "org.scalikejdbc"        %% "scalikejdbc"    % "2.3.5",
  "org.slf4j"               % "slf4j-log4j12"  % "1.7.18",
  "org.spark-project.akka" %% "akka-actor"     % "2.3.4-spark",
  "org.specs2"             %% "specs2"         % "2.3.13" % "test")

parallelExecution in Test := false

pomExtra := childrenPomExtra.value

