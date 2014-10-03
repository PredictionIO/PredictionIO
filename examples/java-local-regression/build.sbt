
import AssemblyKeys._

assemblySettings

name := "java-local-regression"

organization := "myorg"

version := "0.0.1-SNAPSHOT"

libraryDependencies ++= Seq(
  "io.prediction"    %% "core"          % "0.8.1-SNAPSHOT" % "provided",
  "org.apache.spark" %% "spark-core"    % "1.1.0" % "provided")
