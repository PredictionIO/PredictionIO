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

name := "apache-predictionio-data-hdfs"

val Hadoop27 = config("hadoop27") extend Compile
val Hadoop26 = config("hadoop26") extend Compile

configs(Hadoop27, Hadoop26)

libraryDependencies ++= Seq(
  "org.apache.hadoop"       % "hadoop-common" % "2.7.3" % "hadoop27" exclude("commons-beanutils", "*"),
  "org.apache.hadoop"       % "hadoop-common" % "2.6.5" % "hadoop26" exclude("commons-beanutils", "*"),
  "org.apache.predictionio" %% "apache-predictionio-data" % version.value,
  "org.scalatest"           %% "scalatest"      % "2.1.7" % "test")

compile in Compile := inc.Analysis.Empty

lazy val customAssemblySettings: Seq[Def.Setting[_]] =
  inConfig(Hadoop27)(
    Classpaths.configSettings ++ Defaults.configTasks ++ baseAssemblySettings ++ Seq(
      assemblyOption in assembly := (assemblyOption in assembly).value.copy(
        includeScala = false,
        excludedJars = (fullClasspath in assembly).value.filter {_.data.getName startsWith "apache-predictionio"}
      ),
      assemblyOutputPath in assembly := baseDirectory.value.getAbsoluteFile.getParentFile.getParentFile /
        "assembly" / scalaBinaryVersion.value / "spark" / s"pio-data-hdfs27-assembly-${version.value}.jar"
    )
  ) ++
  inConfig(Hadoop26)(
    Classpaths.configSettings ++ Defaults.configTasks ++ baseAssemblySettings ++ Seq(
      assemblyOption in assembly := (assemblyOption in assembly).value.copy(
        includeScala = false,
        excludedJars = (fullClasspath in assembly).value.filter {_.data.getName startsWith "apache-predictionio"}
      ),
      assemblyOutputPath in assembly := baseDirectory.value.getAbsoluteFile.getParentFile.getParentFile /
        "assembly" / scalaBinaryVersion.value / "spark" / s"pio-data-hdfs26-assembly-${version.value}.jar"
    )
  )

Seq(customAssemblySettings: _*)

pomExtra := childrenPomExtra.value
