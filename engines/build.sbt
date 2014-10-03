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

name := "engines"

libraryDependencies ++= Seq(
  "com.github.scopt"  %% "scopt"          % "3.2.0",
  "commons-io"         % "commons-io"     % "2.4",
  "org.apache.commons" % "commons-math3"  % "3.3",
  "org.apache.mahout"  % "mahout-core"    % "0.9",
  "org.apache.spark"  %% "spark-core"     % sparkVersion.value % "provided",
  "org.apache.spark"  %% "spark-mllib"    % sparkVersion.value % "provided",
  "org.clapper"       %% "grizzled-slf4j" % "1.0.2",
  "org.json4s"        %% "json4s-native"  % json4sVersion.value,
  "org.scalatest"     %% "scalatest"      % "2.2.0" % "test")

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case PathList("scala", "reflect", "api", xs @ _*) => MergeStrategy.last
    case PathList("org", "xmlpull", xs @ _*) => MergeStrategy.last
    case x => old(x)
  }
}

excludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
  cp filter { Seq(
    "asm-3.1.jar",
    "commons-beanutils-1.7.0.jar",
    "commons-collections-3.2.1.jar") contains _.data.getName
  }
}

run in Compile <<= Defaults.runTask(
  fullClasspath in Compile,
  mainClass in (Compile, run),
  runner in (Compile, run))

runMain in Compile <<= Defaults.runMainTask(
  fullClasspath in Compile,
  runner in (Compile, run))

net.virtualvoid.sbt.graph.Plugin.graphSettings
