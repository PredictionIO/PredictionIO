// Copyright 2014 TappingStone, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

name := "data"

libraryDependencies ++= Seq(
  "com.google.guava"        % "guava"           % "18.0",
  "io.spray"                % "spray-can"       % "1.2.1",
  "io.spray"                % "spray-routing"   % "1.2.1",
  "org.apache.hadoop"       % "hadoop-common"   % "2.5.0",
  "org.apache.hbase"        % "hbase-common"    % "0.98.5-hadoop2",
  "org.apache.hbase"        % "hbase-client"    % "0.98.5-hadoop2",
  "org.clapper"            %% "grizzled-slf4j"  % "1.0.2",
  "org.elasticsearch"       % "elasticsearch"   % elasticsearchVersion.value,
  "org.json4s"             %% "json4s-native"   % json4sVersion.value,
  "org.json4s"             %% "json4s-ext"      % json4sVersion.value,
  "org.mongodb"            %% "casbah"          % "2.7.2",
  "org.scalatest"          %% "scalatest"       % "2.1.6" % "test",
  "org.slf4j"               % "slf4j-log4j12"   % "1.7.7",
  "commons-codec"           % "commons-codec"   % "1.9",
  "org.spark-project.akka" %% "akka-actor"      % "2.2.3-shaded-protobuf",
  "org.specs2"             %% "specs2"          % "2.3.13" % "test",
  // added for Parallel storage interface
  "org.apache.hbase" % "hbase-server" % "0.98.5-hadoop2"
    exclude("org.mortbay.jetty", "servlet-api-2.5")
    exclude("org.mortbay.jetty", "jsp-api-2.1")
    exclude("org.mortbay.jetty", "jsp-2.1"),
  "org.apache.spark" %% "spark-core" % sparkVersion.value % "provided")
