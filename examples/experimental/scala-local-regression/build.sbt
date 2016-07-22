import AssemblyKeys._

assemblySettings

name := "example-scala-local-regression"

organization := "org.apache.predictionio"

libraryDependencies ++= Seq(
  "org.apache.predictionio"    %% "core"          % "0.9.1" % "provided",
  "org.apache.spark" %% "spark-core"    % "1.2.0" % "provided",
  "org.json4s"       %% "json4s-native" % "3.2.10",
  "org.scalanlp"     %% "nak"           % "1.3")
