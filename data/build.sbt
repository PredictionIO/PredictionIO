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

import PIOBuild._

name := "apache-predictionio-data"

libraryDependencies ++= Seq(
  "org.scala-lang"          % "scala-reflect"  % scalaVersion.value,
  "com.github.nscala-time" %% "nscala-time"    % "2.22.0",
  "com.google.guava"        % "guava"          % "27.1-jre",
  "com.typesafe.akka"      %% "akka-http-testkit" % "10.1.8" % "test",
  "org.apache.spark"       %% "spark-sql"      % sparkVersion.value % "provided",
  "org.clapper"            %% "grizzled-slf4j" % "1.3.3"
    exclude("org.slf4j", "slf4j-api"),
  "org.specs2"             %% "specs2-core"    % "4.5.1" % "test",
  "org.scalamock"          %% "scalamock"      % "4.2.0" % "test",
  "com.h2database"          % "h2"             % "1.4.199" % "test")

parallelExecution in Test := false

pomExtra := childrenPomExtra.value
