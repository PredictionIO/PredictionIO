name := "template-scala-parallel-recommendation"

scalaVersion := "2.11.12"
libraryDependencies ++= Seq(
  "org.apache.predictionio" %% "apache-predictionio-core" % "0.14.0-SNAPSHOT" % "provided",
  "org.apache.spark"        %% "spark-mllib"              % "2.1.1" % "provided")
