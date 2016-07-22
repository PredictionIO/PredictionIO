import AssemblyKeys._

assemblySettings

name := "template-scala-parallel-recommendation-custom-query"

organization := "org.apache.predictionio"

def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")

libraryDependencies ++= provided(
  "org.apache.predictionio"    %% "core"          % "0.8.6",
  "org.apache.spark" %% "spark-core"    % "1.2.0",
  "org.apache.spark" %% "spark-mllib"   % "1.2.0")
