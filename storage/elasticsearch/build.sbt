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

name := "apache-predictionio-data-elasticsearch"

elasticsearchSparkArtifact := (if (majorVersion(sparkVersion.value) == 2) "elasticsearch-spark-20" else "elasticsearch-spark-13")

elasticsearchVersion := (if (majorVersion(elasticsearchVersion.value) < 5) "5.6.9" else elasticsearchVersion.value)

libraryDependencies ++= Seq(
  "org.apache.predictionio" %% "apache-predictionio-core" % version.value % "provided",
  "org.apache.spark"        %% "spark-core"               % sparkVersion.value % "provided",
  "org.elasticsearch.client" % "elasticsearch-rest-client"                     % elasticsearchVersion.value,
  "org.elasticsearch"       %% elasticsearchSparkArtifact.value % elasticsearchVersion.value
    exclude("org.apache.spark", "*"),
  "org.elasticsearch"        % "elasticsearch-hadoop-mr"  % elasticsearchVersion.value,
  "org.specs2"              %% "specs2"                   % "2.3.13" % "test")

parallelExecution in Test := false

pomExtra := childrenPomExtra.value

assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)

assemblyShadeRules in assembly := Seq(
  ShadeRule.rename("org.apache.http.**" ->
    "org.apache.predictionio.shaded.org.apache.http.@1").inAll,
  ShadeRule.rename("org.elasticsearch.client.**" ->
    "org.apache.predictionio.shaded.org.elasticsearch.client.@1").inAll)

// skip test in assembly
test in assembly := {}

assemblyOutputPath in assembly := baseDirectory.value.getAbsoluteFile.getParentFile.getParentFile /
  "assembly" / "src" / "universal" / "lib" / "spark" /
  s"pio-data-elasticsearch-assembly-${version.value}.jar"
