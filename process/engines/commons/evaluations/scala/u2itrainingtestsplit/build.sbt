import xerial.sbt.Pack._

name := "predictionio-process-commons-evaluations-scala-u2itrainingtestsplittime"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.1",
  "ch.qos.logback" % "logback-core" % "1.1.1",
  "com.github.scopt" %% "scopt" % "3.2.0",
  "org.clapper" %% "grizzled-slf4j" % "1.0.1")

packSettings

packJarNameConvention := "full"

packExpandedClasspath := true

packGenerateWindowsBatFile := false

packMain := Map("u2itrainingtestsplit" -> "io.prediction.evaluations.commons.trainingtestsplit.U2ITrainingTestSplitTime")

packJvmOpts := Map("u2itrainingtestsplit" -> Common.packCommonJvmOpts)
