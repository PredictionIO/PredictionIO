package io.prediction.evaluations.commons.topkitems

import io.prediction.commons._
import io.prediction.commons.filepath.OfflineMetricFile
import io.prediction.output.itemrec.ItemRecAlgoOutput
import io.prediction.output.itemsim.ItemSimAlgoOutput

import grizzled.slf4j.Logger
import java.io.{ File, PrintWriter }
import scala.sys.process._

case class TopKItemsConfig(
  enginetype: String = "",
  evalid: Int = 0,
  metricid: Int = 0,
  algoid: Int = 0,
  hdfsroot: String = "",
  k: Int = 0,
  local: Boolean = false)

object TopKItems {
  def main(args: Array[String]) {
    val parser = new scopt.OptionParser[TopKItemsConfig]("topk") {
      head("topk")
      opt[String]("enginetype") required () action { (x, c) =>
        c.copy(enginetype = x)
      } validate { x =>
        x match {
          case "itemrec" | "itemsim" => success
          case _ => failure("--enginetype must be either itemrec or itemsim")
        }
      } text ("engine type (supported: itemrec, itemsim)")
      opt[Int]("evalid") required () action { (x, c) =>
        c.copy(evalid = x)
      } text ("the OfflineEval ID that this metric will be applied to")
      opt[Int]("metricid") required () action { (x, c) =>
        c.copy(metricid = x)
      } text ("the OfflineEvalMetric ID that this metric will be applied to")
      opt[Int]("algoid") required () action { (x, c) =>
        c.copy(algoid = x)
      } text ("the Algo ID that this metric will be applied to")
      opt[String]("hdfsroot") required () action { (x, c) =>
        c.copy(hdfsroot = x)
      } text ("the HDFS root directory location where temporary files will be stored")
      opt[Int]("k") required () action { (x, c) =>
        c.copy(k = x)
      } text ("the k parameter for MAP@k")
      opt[Unit]("local") action { (_, c) =>
        c.copy(local = true)
      } text ("run in local mode, i.e. do not copy the end result to HDFS")
    }

    parser.parse(args, TopKItemsConfig()) map { config =>
      val logger = Logger(TopKItems.getClass)
      val evalid = config.evalid
      val algoid = config.algoid
      val metricid = config.metricid
      val hdfsRoot = config.hdfsroot
      val k = config.k
      val commonsConfig = new Config

      /** Try search path if hadoop home is not set. */
      val hadoopCommand = commonsConfig.settingsHadoopHome map { h => h + "/bin/hadoop" } getOrElse { "hadoop" }

      val apps = commonsConfig.getSettingsApps
      val engines = commonsConfig.getSettingsEngines
      val algos = commonsConfig.getSettingsAlgos
      val offlineEvals = commonsConfig.getSettingsOfflineEvals

      val algo = algos.get(algoid).get
      val offlineEval = offlineEvals.get(evalid).get
      val engine = engines.get(offlineEval.engineid).get
      val app = apps.get(engine.appid).get.copy(id = evalid)

      val tmpFilePath = OfflineMetricFile(commonsConfig.settingsLocalTempRoot, engine.appid, engine.id, evalid, metricid, algoid, "topKItems.tsv")
      val tmpFile = new File(tmpFilePath)
      tmpFile.getParentFile().mkdirs()
      logger.info(s"Dumping data to temporary file $tmpFilePath...")

      config.enginetype match {
        case "itemrec" => {
          val users = commonsConfig.getAppdataTrainingUsers
          var userCount = 0
          printToFile(tmpFile) { p =>
            users.getByAppid(evalid) foreach { u =>
              val topKItems = ItemRecAlgoOutput.output(u.id, k, None, None, None, None)(app, engine, algo, Some(offlineEval))
              if (topKItems.length > 0) {
                userCount += 1
                val topKString = topKItems.map(iid => s"${evalid}_${iid}").mkString(",")
                p.println(s"${evalid}_${u.id}\t${topKString}")
              }
            }
            logger.info(s"Found $userCount user(s) with non-zero top-K items")
          }
        }
        case "itemsim" => {
          val items = commonsConfig.getAppdataTrainingItems
          val scores = Seq.range(1, k + 1).reverse
          var itemCount = 0
          printToFile(tmpFile) { p =>
            items.getByAppid(evalid) foreach { i =>
              val topKItems = ItemSimAlgoOutput.output(i.id, k, None, None, None, None)(app, engine, algo, Some(offlineEval))
              if (topKItems.length > 0) {
                itemCount += 1
                topKItems.zip(scores) foreach { tuple =>
                  val (iid, score) = tuple
                  p.println(s"${evalid}_${i.id}\t${evalid}_${iid}\t${score}")
                }
              }
            }
            logger.info(s"Found ${itemCount} item(s) with non-zero top-K items")
          }
        }
      }

      if (!config.local) {
        tmpFile.deleteOnExit
        val hdfsFilePath = OfflineMetricFile(hdfsRoot, engine.appid, engine.id, evalid, metricid, algoid, "topKItems.tsv")
        val rmCommand = s"$hadoopCommand fs -rm $hdfsFilePath"
        logger.info(s"Executing '${rmCommand}'...")
        rmCommand.!
        val copyCommand = s"$hadoopCommand fs -copyFromLocal $tmpFilePath $hdfsFilePath"
        logger.info(s"Executing '${copyCommand}'...")
        copyCommand.!
      }

      logger.info("Finished")
    }
  }

  def printToFile(f: java.io.File)(op: java.io.PrintWriter => Unit) {
    val p = new java.io.PrintWriter(f)
    try { op(p) } finally { p.close() }
  }
}
