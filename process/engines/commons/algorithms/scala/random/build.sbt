import xerial.sbt.Pack._

name := "predictionio-process-commons-algorithms-scala-random"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.1",
  "ch.qos.logback" % "logback-core" % "1.1.1",
  "com.github.scopt" %% "scopt" % "3.2.0",
  "org.clapper" %% "grizzled-slf4j" % "1.0.1")

packSettings

packJarNameConvention := "full"

packExpandedClasspath := true

packGenerateWindowsBatFile := false

packMain := Map("random" -> "io.prediction.algorithms.commons.random.Random")

packJvmOpts := Map("random" -> Common.packCommonJvmOpts)
