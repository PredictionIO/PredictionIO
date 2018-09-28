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

lazy val scalaSparkDepsVersion = Map(
  "2.11" -> Map(
    "2.0" -> Map(
      "akka" -> "2.4.17",
      "hadoop" -> "2.7.3",
      "json4s" -> "3.2.11"),
    "2.1" -> Map(
      "akka" -> "2.4.17",
      "hadoop" -> "2.7.3",
      "json4s" -> "3.2.11"),
    "2.2" -> Map(
      "akka" -> "2.4.17",
      "hadoop" -> "2.7.3",
      "json4s" -> "3.2.11"),
    "2.3" -> Map(
      "akka" -> "2.4.17",
      "hadoop" -> "2.7.3",
      "json4s" -> "3.2.11")))

name := "apache-predictionio-parent"

version in ThisBuild := "0.14.0-SNAPSHOT"

organization in ThisBuild := "org.apache.predictionio"

scalaVersion in ThisBuild := sys.props.getOrElse("scala.version", "2.11.8")

scalaBinaryVersion in ThisBuild := binaryVersion(scalaVersion.value)

crossScalaVersions in ThisBuild := Seq("2.11.8")

scalacOptions in ThisBuild ++= Seq("-deprecation", "-unchecked", "-feature")

scalacOptions in (ThisBuild, Test) ++= Seq("-Yrangepos")
fork in (ThisBuild, run) := true

javacOptions in (ThisBuild, compile) ++= Seq("-source", "1.8", "-target", "1.8",
  "-Xlint:deprecation", "-Xlint:unchecked")

// Ignore differentiation of Spark patch levels
sparkVersion in ThisBuild := sys.props.getOrElse("spark.version", "2.1.1")

sparkBinaryVersion in ThisBuild := binaryVersion(sparkVersion.value)

akkaVersion in ThisBuild := sys.props.getOrElse(
  "akka.version",
  scalaSparkDepsVersion(scalaBinaryVersion.value)(sparkBinaryVersion.value)("akka"))

lazy val es = sys.props.getOrElse("elasticsearch.version", "5.5.2")

elasticsearchVersion in ThisBuild := es

json4sVersion in ThisBuild := scalaSparkDepsVersion(scalaBinaryVersion.value)(sparkBinaryVersion.value)("json4s")

hadoopVersion in ThisBuild := sys.props.getOrElse(
  "hadoop.version",
  scalaSparkDepsVersion(scalaBinaryVersion.value)(sparkBinaryVersion.value)("hadoop"))

val conf = file("conf")

val commonSettings = Seq(
  autoAPIMappings := true,
  licenseConfigurations := Set("compile"),
  licenseReportTypes := Seq(Csv),
  unmanagedClasspath in Test += conf,
  unmanagedClasspath in Test += baseDirectory.value.getParentFile / s"storage/jdbc/target/scala-${scalaBinaryVersion.value}/classes")

val commonTestSettings = Seq(
  libraryDependencies ++= Seq(
    "org.postgresql"   % "postgresql"  % "9.4-1204-jdbc41" % "test",
    "org.scalikejdbc" %% "scalikejdbc" % "3.1.0" % "test"))

val dataElasticsearch1 = (project in file("storage/elasticsearch1")).
  settings(commonSettings: _*).
  enablePlugins(GenJavadocPlugin)

val dataElasticsearch = (project in file("storage/elasticsearch")).
  settings(commonSettings: _*)

val dataHbase = (project in file("storage/hbase")).
  settings(commonSettings: _*).
  enablePlugins(GenJavadocPlugin)

val dataHdfs = (project in file("storage/hdfs")).
  settings(commonSettings: _*).
  enablePlugins(GenJavadocPlugin)

val dataJdbc = (project in file("storage/jdbc")).
  settings(commonSettings: _*).
  enablePlugins(GenJavadocPlugin)

val dataLocalfs = (project in file("storage/localfs")).
  settings(commonSettings: _*).
  enablePlugins(GenJavadocPlugin)

val dataS3 = (project in file("storage/s3")).
  settings(commonSettings: _*).
  enablePlugins(GenJavadocPlugin)

val common = (project in file("common")).
  settings(commonSettings: _*).
  enablePlugins(GenJavadocPlugin).
  disablePlugins(sbtassembly.AssemblyPlugin)

val data = (project in file("data")).
  dependsOn(common).
  settings(commonSettings: _*).
  settings(commonTestSettings: _*).
  enablePlugins(GenJavadocPlugin).
  settings(unmanagedSourceDirectories in Compile +=
    sourceDirectory.value / s"main/spark-${majorVersion(sparkVersion.value)}").
  disablePlugins(sbtassembly.AssemblyPlugin)

val core = (project in file("core")).
  dependsOn(data).
  settings(commonSettings: _*).
  settings(commonTestSettings: _*).
  enablePlugins(GenJavadocPlugin).
  enablePlugins(BuildInfoPlugin).
  settings(
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      scalaBinaryVersion,
      sbtVersion,
      sparkVersion,
      hadoopVersion),
    buildInfoPackage := "org.apache.predictionio.core"
  ).
  enablePlugins(SbtTwirl).
  disablePlugins(sbtassembly.AssemblyPlugin)

val tools = (project in file("tools")).
  dependsOn(core).
  dependsOn(data).
  settings(commonSettings: _*).
  settings(commonTestSettings: _*).
  settings(skip in publish := true).
  enablePlugins(GenJavadocPlugin).
  enablePlugins(SbtTwirl)

val e2 = (project in file("e2")).
  settings(commonSettings: _*).
  enablePlugins(GenJavadocPlugin).
  disablePlugins(sbtassembly.AssemblyPlugin)

val dataEs = if (majorVersion(es) == 1) dataElasticsearch1 else dataElasticsearch

val storageSubprojects = Seq(
    dataEs,
    dataHbase,
    dataHdfs,
    dataJdbc,
    dataLocalfs,
    dataS3)

val storage = (project in file("storage"))
  .settings(skip in publish := true)
  .aggregate(storageSubprojects map Project.projectToRef: _*)
  .disablePlugins(sbtassembly.AssemblyPlugin)

val assembly = (project in file("assembly")).
  settings(commonSettings: _*)

val root = (project in file(".")).
  settings(commonSettings: _*).
  enablePlugins(ScalaUnidocPlugin).
  settings(
    skip in publish := true,
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(dataElasticsearch, dataElasticsearch1),
    unidocProjectFilter in (JavaUnidoc, unidoc) := inAnyProject -- inProjects(dataElasticsearch, dataElasticsearch1),
    scalacOptions in (ScalaUnidoc, unidoc) ++= Seq(
      "-groups",
      "-skip-packages",
      Seq(
        "akka",
        "org.apache.predictionio.annotation",
        "org.apache.predictionio.authentication",
        "org.apache.predictionio.configuration",
        "org.apache.predictionio.controller.html",
        "org.apache.predictionio.controller.java",
        "org.apache.predictionio.data.api",
        "org.apache.predictionio.data.storage.*",
        "org.apache.predictionio.data.storage.hdfs",
        "org.apache.predictionio.data.storage.jdbc",
        "org.apache.predictionio.data.storage.localfs",
        "org.apache.predictionio.data.storage.s3",
        "org.apache.predictionio.data.storage.hbase",
        "org.apache.predictionio.data.view",
        "org.apache.predictionio.data.webhooks",
        "org.apache.predictionio.tools",
        "org.apache.predictionio.workflow.html",
        "scalikejdbc").mkString(":"),
      "-doc-title",
      "PredictionIO Scala API",
      "-doc-version",
      version.value,
      "-doc-root-content",
      "docs/scaladoc/rootdoc.txt")).
  settings(
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
      "java.lang")).
  aggregate(common, core, data, tools, e2).
  disablePlugins(sbtassembly.AssemblyPlugin)

val pioUnidoc = taskKey[Unit]("Builds PredictionIO ScalaDoc")

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

homepage := Some(url("http://predictionio.apache.org"))

pomExtra := {
  <parent>
    <groupId>org.apache</groupId>
    <artifactId>apache</artifactId>
    <version>18</version>
  </parent>
  <scm>
    <connection>scm:git:github.com/apache/predictionio</connection>
    <developerConnection>scm:git:https://git-wip-us.apache.org/repos/asf/predictionio.git</developerConnection>
    <url>github.com/apache/predictionio</url>
  </scm>
  <developers>
    <developer>
      <id>donald</id>
      <name>Donald Szeto</name>
      <url>http://predictionio.apache.org</url>
      <email>donald@apache.org</email>
    </developer>
  </developers>
}

childrenPomExtra in ThisBuild := {
  <parent>
    <groupId>{organization.value}</groupId>
    <artifactId>{name.value}_{scalaBinaryVersion.value}</artifactId>
    <version>{version.value}</version>
  </parent>
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

printBuildInfo := {
  println(s"PIO_SCALA_VERSION=${scalaVersion.value}")
  println(s"PIO_SPARK_VERSION=${sparkVersion.value}")
  println(s"PIO_ELASTICSEARCH_VERSION=${elasticsearchVersion.value}")
  println(s"PIO_HADOOP_VERSION=${hadoopVersion.value}")
}
