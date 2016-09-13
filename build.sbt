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

import UnidocKeys._

name := "pio"

version in ThisBuild := "0.10.0-SNAPSHOT"

organization in ThisBuild := "org.apache.predictionio"

scalaVersion in ThisBuild := "2.11.8"

lazy val oldScalaVersion = "2.10.5"
lazy val newScalaVersion = "2.11.8"

crossScalaVersions := Seq(oldScalaVersion, newScalaVersion)

scalacOptions in ThisBuild ++= Seq("-deprecation", "-unchecked", "-feature")

scalacOptions in (ThisBuild, Test) ++= Seq("-Yrangepos")

fork in (ThisBuild, run) := true

javacOptions in (ThisBuild, compile) ++= Seq("-source", "1.7", "-target", "1.7",
  "-Xlint:deprecation", "-Xlint:unchecked")

elasticsearchVersion in ThisBuild := "1.4.4"

json4sVersion in ThisBuild := "3.2.10"

sparkVersion in ThisBuild := getSparkVersion(scalaVersion.value)

def getSparkVersion(scalaVersion: String) = scalaVersion match {
  case `oldScalaVersion` => "1.6.2"
  case `newScalaVersion` => "2.0.0"
}

akkaVersion in ThisBuild := getAkkaVersion(scalaVersion.value)

def getAkkaVersion(scalaVersion: String) = scalaVersion match {
  case `oldScalaVersion` => "2.3.15"
  case `newScalaVersion` => "2.4.9"
}

hadoopVersion in ThisBuild := getHadoopVersion(scalaVersion.value)

def getHadoopVersion(scalaVersion: String) = scalaVersion match {
  case `oldScalaVersion` => "2.6.4"
  case `newScalaVersion` => "2.7.3"
}

def scalaVersionPrefix(versionString: String) =
  versionString.split('.').take(2).mkString(".")

lazy val pioBuildInfoSettings = buildInfoSettings ++ Seq(
  sourceGenerators in Compile <+= buildInfo,
  buildInfoKeys := Seq[BuildInfoKey](
    name,
    version,
    scalaVersion,
    sbtVersion,
    sparkVersion,
    hadoopVersion),
  buildInfoPackage := "org.apache.predictionio.core")

// Used temporarily to modify genjavadoc version to "0.10" until unidoc updates it
lazy val genjavadocSettings: Seq[sbt.Def.Setting[_]] = Seq(
  libraryDependencies += compilerPlugin("com.typesafe.genjavadoc" %% "genjavadoc-plugin" % "0.10" cross CrossVersion.full),
    scalacOptions <+= target map (t => "-P:genjavadoc:out=" + (t / "java")))

lazy val conf = file(".") / "conf"

lazy val root = project in file(".") aggregate(
  common,
  core,
  data,
  tools,
  e2)

lazy val common = (project in file("common")).
  settings(unmanagedClasspath in Test += conf)

lazy val core = (project in file("core")).
  dependsOn(data).
  settings(genjavadocSettings: _*).
  settings(pioBuildInfoSettings: _*).
  enablePlugins(SbtTwirl).
  settings(unmanagedClasspath in Test += conf)

lazy val data = (project in file("data")).
  dependsOn(common).
  settings(genjavadocSettings: _*).
  settings(unmanagedClasspath in Test += conf).
  settings(fullClasspath in Test ++= Seq(
    baseDirectory.value / s"../core/target/scala-${scalaVersionPrefix(scalaVersion.value)}/classes",
    baseDirectory.value / s"../core/target/scala-${scalaVersionPrefix(scalaVersion.value)}/test-classes"))
    // Paths added above are required for the tests, since thread pools initialized
    // in unit tests of data subproject are used later in spark jobs executed in core.
    // They need to have properly configured classloaders to load core classes for spark
    // in subsequent tests.

lazy val tools = (project in file("tools")).
  dependsOn(core).
  dependsOn(data).
  enablePlugins(SbtTwirl).
  settings(unmanagedClasspath in Test += conf)

lazy val e2 = (project in file("e2")).
  settings(genjavadocSettings: _*).
  settings(unmanagedClasspath in Test += conf)

scalaJavaUnidocSettings

// scalaUnidocSettings

unidocAllSources in (JavaUnidoc, unidoc) := {
  (unidocAllSources in (JavaUnidoc, unidoc)).value
    .map(_.filterNot(_.getName.contains("$")))
}

scalacOptions in (ScalaUnidoc, unidoc) ++= Seq(
  "-groups",
  "-skip-packages",
  Seq(
    "akka",
    "breeze",
    "html",
    "org.apache.predictionio.annotation",
    "org.apache.predictionio.controller.html",
    "org.apache.predictionio.data.api",
    "org.apache.predictionio.data.view",
    "org.apache.predictionio.workflow",
    "org.apache.predictionio.tools",
    "org",
    "scalikejdbc").mkString(":"),
  "-doc-title",
  "PredictionIO Scala API",
  "-doc-version",
  version.value,
  "-doc-root-content",
  "docs/scaladoc/rootdoc.txt")

javacOptions in (JavaUnidoc, unidoc) := Seq(
  "-subpackages",
  "org.apache.predictionio",
  "-exclude",
  Seq(
    "org.apache.predictionio.controller.html",
    "org.apache.predictionio.data.api",
    "org.apache.predictionio.data.view",
    "org.apache.predictionio.data.webhooks.*",
    "org.apache.predictionio.workflow",
    "org.apache.predictionio.tools",
    "org.apache.hadoop").mkString(":"),
  "-windowtitle",
  "PredictionIO Javadoc " + version.value,
  "-group",
  "Java Controllers",
  Seq(
    "org.apache.predictionio.controller.java",
    "org.apache.predictionio.data.store.java").mkString(":"),
  "-group",
  "Scala Base Classes",
  Seq(
    "org.apache.predictionio.controller",
    "org.apache.predictionio.core",
    "org.apache.predictionio.data.storage",
    "org.apache.predictionio.data.storage.*",
    "org.apache.predictionio.data.store").mkString(":"),
  "-overview",
  "docs/javadoc/javadoc-overview.html",
  "-noqualifier",
  "java.lang")

lazy val pioUnidoc = taskKey[Unit]("Builds PredictionIO ScalaDoc and Javadoc")

pioUnidoc := {
  (unidoc in Compile).value
  val log = streams.value.log
  log.info("Adding custom styling.")
  IO.append(
    crossTarget.value / "unidoc" / "lib" / "template.css",
    IO.read(baseDirectory.value / "docs" / "scaladoc" / "api-docs.css"))
  IO.append(
    crossTarget.value / "unidoc" / "lib" / "template.js",
    IO.read(baseDirectory.value / "docs" / "scaladoc" / "api-docs.js"))
}

pomExtra in ThisBuild := {
  <url>http://predictionio.incubator.apache.org</url>
  <licenses>
    <license>
      <name>Apache 2</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
    </license>
  </licenses>
  <scm>
    <connection>scm:git:github.com/apache/incubator-predictionio</connection>
    <developerConnection>scm:git:git@github.com:apache/incubator-predictionio.git</developerConnection>
    <url>github.com/apache/incubator-predictionio</url>
  </scm>
  <developers>
    <developer>
      <id>pio</id>
      <name>The PredictionIO Team</name>
      <url>http://predictionio.incubator.apache.org</url>
    </developer>
  </developers>
}

concurrentRestrictions in Global := Seq(
  Tags.limit(Tags.CPU, 1),
  Tags.limit(Tags.Network, 1),
  Tags.limit(Tags.Test, 1),
  Tags.limitAll( 1 )
)

parallelExecution := false

parallelExecution in Global := false

testOptions in Test += Tests.Argument("-oDF")
