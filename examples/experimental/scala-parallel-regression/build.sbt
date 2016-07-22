
import AssemblyKeys._

assemblySettings

name := "scala-parallel-regression"

organization := "myorg"

version := "0.0.1-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.apache.predictionio"    %% "core"          % "0.9.1" % "provided",
  "org.apache.spark" %% "spark-core"    % "1.2.0" % "provided",
  "org.apache.spark"  %% "spark-mllib"    % "1.2.0"
    exclude("org.apache.spark", "spark-core_2.10")
    exclude("org.eclipse.jetty", "jetty-server"))
