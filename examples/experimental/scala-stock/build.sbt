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

import AssemblyKeys._

assemblySettings

name := "example-scala-stock"

organization := "org.apache.predictionio"

libraryDependencies ++= Seq(
  "org.apache.predictionio"     %% "core"           % "0.9.1" % "provided",
  "org.apache.predictionio"     %% "engines"        % "0.9.1" % "provided",
  "com.github.scopt"  %% "scopt"          % "3.2.0",
  "commons-io"         % "commons-io"     % "2.4",
  "org.apache.commons" % "commons-math3"  % "3.3",
  "org.apache.mahout"  % "mahout-core"    % "0.9",
  "org.apache.spark"  %% "spark-core"     % "1.2.0" % "provided",
  "org.apache.spark"  %% "spark-mllib"    % "1.2.0"
    exclude("org.apache.spark", "spark-core_2.10")
    exclude("org.eclipse.jetty", "jetty-server"),
  "org.clapper"       %% "grizzled-slf4j" % "1.0.2",
  "org.json4s"        %% "json4s-native"  % "3.2.10",
  "org.scala-saddle"  %% "saddle-core"    % "1.3.2"
    exclude("ch.qos.logback", "logback-classic"),
  "org.scalanlp"      %% "breeze"         % "0.9",
  "org.scalanlp"      %% "breeze-natives" % "0.9",
  "org.scalanlp"      %% "nak"            % "1.3",
  "org.scalatest"     %% "scalatest"      % "2.2.0" % "test")


mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case PathList("scala", xs @ _*) => MergeStrategy.discard
    case PathList("org", "xmlpull", xs @ _*) => MergeStrategy.last
    case x => old(x)
  }
}

lazy val root = (project in file(".")).enablePlugins(SbtTwirl)
