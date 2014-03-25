package io.prediction.evaluations.commons.trainingtestsplit

import io.prediction.commons.filepath.U2ITrainingTestSplitFile

import java.io.File
import scala.io.Source
import scala.sys.process._

import grizzled.slf4j.Logger

case class U2ITrainingTestSplitTimeConfig(
  hadoop: String = "",
  pdioEvalJar: String = "",
  hdfsRoot: String = "",
  localTempRoot: String = "",
  appid: Int = 0,
  engineid: Int = 0,
  evalid: Int = 0,
  sequenceNum: Int = 0)

/**
 * Wrapper for Scalding U2ITrainingTestSplitTime job
 *
 * Args:
 * --hadoop <string> hadoop command
 * --pdioEvalJar <string> the name of the Scalding U2ITrainingTestSplit job jar
 * --sequenceNum. <int>. the sequence number (starts from 1 for the 1st iteration and then increment for later iterations)
 *
 * --dbType: <string> appdata DB type
 * --dbName: <string>
 * --dbHost: <string>. optional. (eg. "127.0.0.1")
 * --dbPort: <int>. optional. (eg. 27017)
 *
 * --training_dbType: <string> training_appadta DB type
 * --training_dbName: <string>
 * --training_dbHost: <string>. optional
 * --training_dbPort: <int>. optional
 *
 * --validation_dbType: <string> validation_appdata DB type
 * --validation_dbName: <string>
 * --validation_dbHost: <string>. optional
 * --validation_dbPort: <int>. optional
 *
 * --test_dbType: <string> test_appdata DB type
 * --test_dbName: <string>
 * --test_dbHost: <string>. optional
 * --test_dbPort: <int>. optional
 *
 * --hdfsRoot: <string>. Root directory of the HDFS
 *
 * --appid: <int>
 * --engineid: <int>
 * --evalid: <int>
 *
 * --itypes: <string separated by white space>. eg "--itypes type1 type2". If no --itypes specified, then ALL itypes will be used.
 *
 * --trainingPercent: <double> (0.01 to 1). training set percentage
 * --validationPercent: <dboule> (0.01 to 1). validation set percentage
 * --testPercent: <double> (0.01 to 1). test set percentage
 * --timeorder: <boolean>. Require total percentage < 1
 *
 */
object U2ITrainingTestSplitTime {
  def main(args: Array[String]) {
    val parser = new scopt.OptionParser[U2ITrainingTestSplitTimeConfig]("u2itrainingtestsplit") {
      head("u2itrainingtestsplit")
      opt[String]("hadoop") required () action { (x, c) =>
        c.copy(hadoop = x)
      } text ("path to the 'hadoop' command")
      opt[String]("pdioEvalJar") required () action { (x, c) =>
        c.copy(pdioEvalJar = x)
      } text ("path to PredictionIO Hadoop job JAR")
      opt[String]("hdfsRoot") required () action { (x, c) =>
        c.copy(hdfsRoot = x)
      } text ("PredictionIO root path in HDFS")
      opt[String]("localTempRoot") required () action { (x, c) =>
        c.copy(localTempRoot = x)
      } text ("local directory for temporary storage")
      opt[Int]("appid") required () action { (x, c) =>
        c.copy(appid = x)
      } text ("the App ID of this offline evaluation")
      opt[Int]("engineid") required () action { (x, c) =>
        c.copy(engineid = x)
      } text ("the Engine ID of this offline evaluation")
      opt[Int]("evalid") required () action { (x, c) =>
        c.copy(evalid = x)
      } text ("the OfflineEval ID of this offline evaluation")
      opt[Int]("sequenceNum") required () action { (x, c) =>
        c.copy(sequenceNum = x)
      } validate { x =>
        if (x >= 1) success else failure("--sequenceNum must be >= 1")
      } text ("sequence (iteration) number of the offline evaluation")
      override def errorOnUnknownArgument = false
    }
    val logger = Logger(U2ITrainingTestSplitTime.getClass)

    parser.parse(args, U2ITrainingTestSplitTimeConfig()) map { config =>
      val hadoop = config.hadoop
      val pdioEvalJar = config.pdioEvalJar
      val hdfsRoot = config.hdfsRoot
      val localTempRoot = config.localTempRoot
      val appid = config.appid
      val engineid = config.engineid
      val evalid = config.evalid
      val sequenceNum = config.sequenceNum
      val argsString = args.mkString(" ")
      val resplit = sequenceNum > 1

      /** command */
      if (!resplit) {
        // prep
        val splitPrepCmd = hadoop + " jar " + pdioEvalJar + " io.prediction.evaluations.scalding.commons.u2itrainingtestsplit.U2ITrainingTestSplitTimePrep " + argsString
        executeCommandAndCheck(splitPrepCmd)
      }

      // copy the count to local tmp
      val hdfsCountPath = U2ITrainingTestSplitFile(hdfsRoot, appid, engineid, evalid, "u2iCount.tsv")
      val localCountPath = localTempRoot + "eval-" + evalid + "-u2iCount.tsv"
      val localCountFile = new File(localCountPath)

      // create parent dir
      localCountFile.getParentFile().mkdirs()

      // delete existing file first
      if (localCountFile.exists()) localCountFile.delete()

      // get the count from hdfs
      val getHdfsCountCmd = hadoop + " fs -getmerge " + hdfsCountPath + " " + localCountPath
      executeCommandAndCheck(getHdfsCountCmd)

      // read the local file and get the count
      val lines = Source.fromFile(localCountPath).getLines
      if (lines.isEmpty) throw new RuntimeException(s"Count file $localCountPath is empty")

      val count = lines.next

      // split
      val splitCmd = hadoop + " jar " + pdioEvalJar + " io.prediction.evaluations.scalding.commons.u2itrainingtestsplit.U2ITrainingTestSplitTime " + argsString + " --totalCount " + count
      executeCommandAndCheck(splitCmd)

      // delete local tmp file
      logger.info(s"Deleting temporary file $localCountPath...")
      localCountFile.delete()
    }

    def executeCommandAndCheck(cmd: String) = {
      logger.info(s"Executing $cmd...")
      if ((cmd.!) != 0) throw new RuntimeException(s"Failed to execute '$cmd'")
    }
  }
}
