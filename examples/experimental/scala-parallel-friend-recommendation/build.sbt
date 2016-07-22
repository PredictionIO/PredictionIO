import AssemblyKeys._

assemblySettings

name := "template-scala-parallel-recommendation-custom-preparator"

organization := "org.apache.predictionio"

libraryDependencies ++= Seq(
  "org.apache.predictionio"    %% "core"          % "0.9.1" % "provided",
  "org.apache.spark" %% "spark-core"    % "1.2.0" % "provided",
  "org.apache.spark" %% "spark-graphx"  % "1.2.0" % "provided")
