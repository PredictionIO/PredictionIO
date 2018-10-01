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
import sbtassembly.AssemblyPlugin.autoImport._

name := "apache-predictionio-tools"

libraryDependencies ++= Seq(
  "com.github.zafarkhaja"  %  "java-semver"       % "0.9.0",
  "org.apache.spark"       %% "spark-sql"         % sparkVersion.value % "provided",
  "com.typesafe.akka"      %% "akka-slf4j"        % akkaVersion.value,
  "com.typesafe.akka"      %% "akka-http-testkit" % "10.1.5" % "test",
  "org.specs2"             %% "specs2-core"       % "4.2.0" % "test")

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "LICENSE.txt") => MergeStrategy.concat
  case PathList("META-INF", "NOTICE.txt")  => MergeStrategy.concat
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

assemblyExcludedJars in assembly := {
  val cp = (fullClasspath in assembly).value
  cp filter { _.data.getName match {
    case "reflectasm-1.10.1.jar" => true
    case "kryo-3.0.3.jar" => true
    case _ => false
  }}
}

assemblyShadeRules in assembly := Seq(
  ShadeRule.rename("org.objenesis.**" -> "shadeio.@1").inLibrary("com.esotericsoftware.kryo" % "kryo" % "2.21").inProject,
  ShadeRule.rename("com.esotericsoftware.reflectasm.**" -> "shadeio.@1").inLibrary("com.esotericsoftware.kryo" % "kryo" % "2.21").inProject,
  ShadeRule.rename("com.esotericsoftware.minlog.**" -> "shadeio.@1").inLibrary("com.esotericsoftware.kryo" % "kryo" % "2.21").inProject
)

// skip test in assembly
test in assembly := {}

assemblyOutputPath in assembly := baseDirectory.value.getAbsoluteFile.getParentFile /
  "assembly" / "src" / "universal" / "lib" / s"pio-assembly-${version.value}.jar"

cleanFiles += baseDirectory.value.getParentFile /
  "assembly" / "src" / "universal" / "lib" 

pomExtra := childrenPomExtra.value
