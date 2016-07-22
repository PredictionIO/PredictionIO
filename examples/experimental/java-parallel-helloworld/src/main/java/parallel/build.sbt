import AssemblyKeys._

assemblySettings

name := "example-java-parallel"

organization := "org.apache.predictionio.examples.java"

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "org.apache.predictionio" %% "core" % "0.8.0-SNAPSHOT" % "provided",
  "org.apache.predictionio" %% "data" % "0.8.0-SNAPSHOT" % "provided",
  "org.apache.spark" %% "spark-core" % "1.0.2" % "provided")
