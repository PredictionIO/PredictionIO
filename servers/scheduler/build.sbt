name := "predictionio-scheduler"

version := "0.7.1-SNAPSHOT"

organization := "io.prediction"

libraryDependencies ++= Seq(
  "io.prediction" %% "predictionio-commons" % version.value,
  "io.prediction" %% "predictionio-output" % version.value,
  "commons-io" % "commons-io" % "2.4",
  "mysql" % "mysql-connector-java" % "5.1.22",
  "org.clapper" %% "scalasti" % "1.0.0",
  "org.quartz-scheduler" % "quartz" % "2.1.7",
  "org.specs2" %% "specs2" % "1.14" % "test",
  "org.mockito" % "mockito-core" % "1.9.5",
  "org.fusesource" % "sigar" % "1.6.4")

javaOptions in Test += "-Dconfig.file=conf/test.conf"

javaOptions += "-Djava.library.path=lib/"

play.Project.playScalaSettings

scalariformSettings
