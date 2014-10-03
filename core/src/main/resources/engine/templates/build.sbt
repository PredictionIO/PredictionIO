name := "myengine"

organization := "org.myorg"

scalaVersion := "2.10.4"

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature")

libraryDependencies ++= Seq("io.prediction" %% "core" % "0.8.1-SNAPSHOT")
