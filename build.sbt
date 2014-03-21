name := "predictionio"

version in ThisBuild := "0.7.0-SNAPSHOT"

organization in ThisBuild := "io.prediction"

scalaVersion in ThisBuild := "2.10.3"

scalacOptions in ThisBuild ++= Seq("-deprecation", "-unchecked", "-feature")

scalacOptions in (ThisBuild, Test) ++= Seq("-Yrangepos")

javacOptions in ThisBuild ++= Seq("-source", "1.6", "-target", "1.6", "-Xlint:deprecation", "-Xlint:unchecked")

libraryDependencies in ThisBuild ++= Seq(
  "com.github.nscala-time" %% "nscala-time" % "0.6.0",
  "org.slf4j" % "slf4j-log4j12" % "1.7.5" % "test",
  "org.specs2" %% "specs2" % "2.3.10" % "test")

publishTo in ThisBuild := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository")))

publishMavenStyle in ThisBuild := true

parallelExecution in (ThisBuild, Test) := false

lazy val root = project.in(file(".")).aggregate(
  commons,
  output,
  processHadoopScalding,
  processEnginesCommonsAlgoScalaRandom,
  processEnginesCommonsEvalScalaMetricsMAP,
  processEnginesCommonsEvalScalaParamGen,
  processEnginesCommonsEvalScalaTopKItems,
  processEnginesCommonsEvalScalaU2ITrainingTestSplit,
  processEnginesItemRecAlgoScalaMahout,
  processEnginesItemSimEvalScalaTopKItems,
  toolsConncheck,
  toolsSettingsInit,
  toolsSoftwareManager,
  toolsUsers)

// Commons and Output

lazy val commons = project in file("commons") settings(scalariformSettings: _*)

lazy val output = project.in(file("output")).dependsOn(commons).settings(scalariformSettings: _*)

// Process Assemblies

lazy val processHadoopScalding = project
  .in(file("process"))
  .aggregate(
    processCommonsHadoopScalding,
    processEnginesCommonsEvalHadoopScalding,
    processEnginesItemRecAlgoHadoopScalding,
    processEnginesItemRecEvalHadoopScalding,
    processEnginesItemSimAlgoHadoopScalding,
    processEnginesItemSimEvalHadoopScalding)
  .dependsOn(
    processCommonsHadoopScalding,
    processEnginesCommonsEvalHadoopScalding,
    processEnginesItemRecAlgoHadoopScalding,
    processEnginesItemRecEvalHadoopScalding,
    processEnginesItemSimAlgoHadoopScalding,
    processEnginesItemSimEvalHadoopScalding)

lazy val processCommonsHadoopScalding = project
  .in(file("process/commons/hadoop/scalding")).dependsOn(commons)
  .settings(scalariformSettings: _*)

lazy val processEnginesCommonsAlgoScalaRandom = project
  .in(file("process/engines/commons/algorithms/scala/random"))
  .dependsOn(commons)
  .settings(scalariformSettings: _*)

lazy val processEnginesCommonsEvalHadoopScalding = project
  .in(file("process/engines/commons/evaluations/hadoop/scalding"))
  .aggregate(processEnginesCommonsEvalHadoopScaldingU2ITrainingTestSplit)
  .dependsOn(processEnginesCommonsEvalHadoopScaldingU2ITrainingTestSplit)

lazy val processEnginesCommonsEvalHadoopScaldingU2ITrainingTestSplit = project
  .in(file("process/engines/commons/evaluations/hadoop/scalding/u2itrainingtestsplit"))
  .dependsOn(processCommonsHadoopScalding)

lazy val processEnginesCommonsEvalScalaMetricsMAP = project
  .in(file("process/engines/commons/evaluations/scala/map"))
  .dependsOn(commons)
  .settings(scalariformSettings: _*)

lazy val processEnginesCommonsEvalScalaParamGen = project
  .in(file("process/engines/commons/evaluations/scala/paramgen"))
  .dependsOn(commons)
  .settings(scalariformSettings: _*)

lazy val processEnginesCommonsEvalScalaTopKItems = project
  .in(file("process/engines/commons/evaluations/scala/topkitems"))
  .dependsOn(commons, output)
  .settings(scalariformSettings: _*)

lazy val processEnginesCommonsEvalScalaU2ISplit = project
  .in(file("process/engines/commons/evaluations/scala/u2isplit"))
  .dependsOn(commons)
  .settings(scalariformSettings: _*)

lazy val processEnginesCommonsEvalScalaU2ITrainingTestSplit = project
  .in(file("process/engines/commons/evaluations/scala/u2itrainingtestsplit"))
  .dependsOn(commons)

lazy val processEnginesItemRecAlgoHadoopScalding = project
  .in(file("process/engines/itemrec/algorithms/hadoop/scalding"))
  .aggregate(
    processEnginesItemRecAlgoHadoopScaldingGeneric,
    processEnginesItemRecAlgoHadoopScaldingKnnitembased,
    processEnginesItemRecAlgoHadoopScaldingRandomrank,
    processEnginesItemRecAlgoHadoopScaldingLatestrank,
    processEnginesItemRecAlgoHadoopScaldingMahout)
  .dependsOn(
    processEnginesItemRecAlgoHadoopScaldingGeneric,
    processEnginesItemRecAlgoHadoopScaldingKnnitembased,
    processEnginesItemRecAlgoHadoopScaldingRandomrank,
    processEnginesItemRecAlgoHadoopScaldingLatestrank,
    processEnginesItemRecAlgoHadoopScaldingMahout)

lazy val processEnginesItemRecAlgoHadoopScaldingGeneric = project
  .in(file("process/engines/itemrec/algorithms/hadoop/scalding/generic"))
  .dependsOn(processCommonsHadoopScalding)
  .settings(scalariformSettings: _*)

lazy val processEnginesItemRecAlgoHadoopScaldingKnnitembased = project
  .in(file("process/engines/itemrec/algorithms/hadoop/scalding/knnitembased"))
  .dependsOn(processCommonsHadoopScalding)
  .settings(scalariformSettings: _*)

lazy val processEnginesItemRecAlgoHadoopScaldingRandomrank = project
  .in(file("process/engines/itemrec/algorithms/hadoop/scalding/randomrank"))
  .dependsOn(processCommonsHadoopScalding)
  .settings(scalariformSettings: _*)

lazy val processEnginesItemRecAlgoHadoopScaldingLatestrank = project
  .in(file("process/engines/itemrec/algorithms/hadoop/scalding/latestrank"))
  .dependsOn(processCommonsHadoopScalding)
  .settings(scalariformSettings: _*)

lazy val processEnginesItemRecAlgoHadoopScaldingMahout = project
  .in(file("process/engines/itemrec/algorithms/hadoop/scalding/mahout"))
  .dependsOn(processCommonsHadoopScalding)
  .settings(scalariformSettings: _*)

lazy val processEnginesItemRecAlgoScalaMahout = project
  .in(file("process/engines/itemrec/algorithms/scala/mahout"))
  .dependsOn(commons)
  .settings(scalariformSettings: _*)

lazy val processEnginesItemRecAlgoScalaGeneric = project
  .in(file("process/engines/itemrec/algorithms/scala/generic"))
  .dependsOn(commons)
  .settings(scalariformSettings: _*)

lazy val processEnginesItemRecAlgoScalaGraphChi = project
  .in(file("process/engines/itemrec/algorithms/scala/graphchi"))
  .dependsOn(commons)
  .settings(scalariformSettings: _*)

lazy val processEnginesItemRecEvalHadoopScalding = project
  .in(file("process/engines/itemrec/evaluations/hadoop/scalding"))
  .aggregate(
    processEnginesItemRecEvalHadoopScaldingMetricsMAP)
  .dependsOn(
    processEnginesItemRecEvalHadoopScaldingMetricsMAP)

lazy val processEnginesItemRecEvalHadoopScaldingMetricsMAP = project
  .in(file("process/engines/itemrec/evaluations/hadoop/scalding/metrics/map"))
  .dependsOn(processCommonsHadoopScalding)
  .settings(scalariformSettings: _*)

lazy val processEnginesItemSimAlgoHadoopScalding = project
  .in(file("process/engines/itemsim/algorithms/hadoop/scalding"))
  .aggregate(
    processEnginesItemSimAlgoHadoopScaldingItemSimCF,
    processEnginesItemSimAlgoHadoopScaldingLatestRank,
    processEnginesItemSimAlgoHadoopScaldingMahout,
    processEnginesItemSimAlgoHadoopScaldingRandomRank)
  .dependsOn(
    processEnginesItemSimAlgoHadoopScaldingItemSimCF,
    processEnginesItemSimAlgoHadoopScaldingLatestRank,
    processEnginesItemSimAlgoHadoopScaldingMahout,
    processEnginesItemSimAlgoHadoopScaldingRandomRank)

lazy val processEnginesItemSimAlgoHadoopScaldingItemSimCF = project
  .in(file("process/engines/itemsim/algorithms/hadoop/scalding/itemsimcf"))
  .dependsOn(processCommonsHadoopScalding)
  .settings(scalariformSettings: _*)

lazy val processEnginesItemSimAlgoHadoopScaldingLatestRank = project
  .in(file("process/engines/itemsim/algorithms/hadoop/scalding/latestrank"))
  .dependsOn(processCommonsHadoopScalding)
  .settings(scalariformSettings: _*)

lazy val processEnginesItemSimAlgoHadoopScaldingMahout = project
  .in(file("process/engines/itemsim/algorithms/hadoop/scalding/mahout"))
  .dependsOn(processCommonsHadoopScalding)
  .settings(scalariformSettings: _*)

lazy val processEnginesItemSimAlgoHadoopScaldingRandomRank = project
  .in(file("process/engines/itemsim/algorithms/hadoop/scalding/randomrank"))
  .dependsOn(processCommonsHadoopScalding)
  .settings(scalariformSettings: _*)

lazy val processEnginesItemSimAlgoScalaGeneric = project
  .in(file("process/engines/itemsim/algorithms/scala/generic"))
  .dependsOn(commons)
  .settings(scalariformSettings: _*)

lazy val processEnginesItemSimAlgoScalaMahout = project
  .in(file("process/engines/itemsim/algorithms/scala/mahout"))
  .dependsOn(commons)
  .settings(scalariformSettings: _*)

lazy val processEnginesItemSimAlgoScalaGraphChi = project
  .in(file("process/engines/itemsim/algorithms/scala/graphchi"))
  .dependsOn(commons)
  .settings(scalariformSettings: _*)

lazy val processEnginesItemSimEvalHadoopScalding = project
  .in(file("process/engines/itemsim/evaluations/hadoop/scalding"))
  .aggregate(
    processEnginesItemSimEvalHadoopScaldingMetricsISMAP)
  .dependsOn(
    processEnginesItemSimEvalHadoopScaldingMetricsISMAP)

lazy val processEnginesItemSimEvalHadoopScaldingMetricsISMAP = project
  .in(file("process/engines/itemsim/evaluations/hadoop/scalding/metrics/ismap"))
  .dependsOn(processCommonsHadoopScalding)
  .settings(scalariformSettings: _*)

lazy val processEnginesItemSimEvalScalaTopKItems = project
  .in(file("process/engines/itemsim/evaluations/scala/topkitems"))
  .dependsOn(commons, output)
  .settings(scalariformSettings: _*)

// Tools Section

lazy val toolsConncheck = project.in(file("tools/conncheck"))
  .dependsOn(commons)
  .settings(scalariformSettings: _*)

lazy val toolsSettingsInit = project.in(file("tools/settingsinit"))
  .dependsOn(commons)
  .settings(scalariformSettings: _*)

lazy val toolsSoftwareManager = project.in(file("tools/softwaremanager"))
  .dependsOn(commons)
  .settings(scalariformSettings: _*)

lazy val toolsUsers = project.in(file("tools/users"))
  .dependsOn(commons)
  .settings(scalariformSettings: _*)
