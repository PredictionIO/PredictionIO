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
  "com.github.nscala-time" %% "nscala-time"    % "2.6.0",
  "com.google.guava"        % "guava"          % "14.0.1",
  "com.typesafe.akka"      %% "akka-http-testkit" % "10.1.5" % "test",
  "org.apache.spark"       %% "spark-sql"      % sparkVersion.value % "provided",
  "org.clapper"            %% "grizzled-slf4j" % "1.0.2",
  "org.scalatest"          %% "scalatest"      % "2.1.7" % "test",
  "org.specs2"             %% "specs2"         % "3.3.1" % "test"
    exclude("org.scalaz.stream", s"scalaz-stream_${scalaBinaryVersion.value}"),
  "org.scalamock"          %% "scalamock-specs2-support" % "3.5.0" % "test",
  "com.h2database"           % "h2"             % "1.4.196" % "test")

parallelExecution in Test := false

pomExtra := childrenPomExtra.value
