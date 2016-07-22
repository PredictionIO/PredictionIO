import AssemblyKeys._

assemblySettings

name := "examples-friendrecommendation"

organization := "org.apache.predictionio"

libraryDependencies ++= Seq(
  "org.apache.predictionio" %% "core" % "0.9.1" % "provided",
  "org.apache.predictionio" %% "data" % "0.9.1" % "provided",
  "org.apache.spark" %% "spark-core" % "1.2.0" % "provided")
