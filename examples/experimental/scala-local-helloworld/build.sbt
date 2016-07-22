import AssemblyKeys._

assemblySettings

name := "example-scala-local-helloworld"

organization := "org.sample"

libraryDependencies ++= Seq(
  "org.apache.predictionio" %% "core" % "0.9.1" % "provided",
  "org.apache.predictionio" %% "data" % "0.9.1" % "provided",
  "org.apache.spark" %% "spark-core" % "1.2.0" % "provided")
