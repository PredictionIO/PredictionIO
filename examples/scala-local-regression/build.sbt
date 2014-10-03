import AssemblyKeys._

assemblySettings

name := "example-scala-local-regression"

organization := "io.prediction"

libraryDependencies ++= Seq(
  "io.prediction"    %% "core"          % "0.8.1-SNAPSHOT" % "provided",
  "org.apache.spark" %% "spark-core"    % "1.1.0" % "provided",
  "org.json4s"       %% "json4s-native" % "3.2.10",
  "org.scalanlp"     %% "nak"           % "1.3")
