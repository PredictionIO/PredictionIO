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

import AssemblyKeys._

assemblySettings

name := "tools"

libraryDependencies ++= Seq(
  "com.github.scopt"       %% "scopt"          % "3.2.0",
  "io.spray"                % "spray-can"      % "1.2.1",
  "io.spray"                % "spray-routing"  % "1.2.1",
  "org.apache.hadoop"       % "hadoop-common"  % "2.5.0",
  "org.apache.hadoop"       % "hadoop-hdfs"    % "2.5.0",
  "org.clapper"            %% "grizzled-slf4j" % "1.0.2",
  "org.json4s"             %% "json4s-native"  % json4sVersion.value,
  "org.json4s"             %% "json4s-ext"     % json4sVersion.value,
  "org.scalaj"             %% "scalaj-http"    % "0.3.16",
  "org.spark-project.akka" %% "akka-actor"     % "2.2.3-shaded-protobuf")

excludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
  cp filter { _.data.getName match {
    case "asm-3.1.jar" => true
    case "commons-beanutils-1.7.0.jar" => true
    case "commons-beanutils-core-1.8.0.jar" => true
    case "slf4j-log4j12-1.7.5.jar" => true
    case _ => false
  }}
}

outputPath in assembly := baseDirectory.value.getAbsoluteFile.getParentFile /
  "assembly" / ("pio-assembly-" + version.value + ".jar")

cleanFiles <+= baseDirectory { base => base.getParentFile / "assembly" }

net.virtualvoid.sbt.graph.Plugin.graphSettings
