
import AssemblyKeys._

assemblySettings

name := "java-local-tutorial"

organization := "org.apache.predictionio"

version := "0.9.1"

libraryDependencies ++= Seq(
  "org.apache.predictionio"    %% "core"          % "0.9.1" % "provided",
  "org.apache.predictionio"    %% "engines"       % "0.9.1" % "provided",
  "org.apache.mahout" % "mahout-core"   % "0.9",
  "org.apache.spark" %% "spark-core"    % "1.2.0" % "provided")
