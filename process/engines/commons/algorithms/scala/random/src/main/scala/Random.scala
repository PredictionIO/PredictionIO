package io.prediction.algorithms.commons.random

import io.prediction.commons.Config
import io.prediction.commons.modeldata.{ ItemRecScore, ItemSimScore }

import com.github.nscala_time.time.Imports._
import grizzled.slf4j.Logger

case class RandomConfig(
  appid: Int = 0,
  engineid: Int = 0,
  algoid: Int = 0,
  evalid: Option[Int] = None,
  itypes: Option[Seq[String]] = None,
  numPredictions: Int = 0,
  modelSet: Boolean = false,
  recommendationTime: DateTime = DateTime.now)

object Random {
  def main(args: Array[String]): Unit = {
    implicit val dateTimeRead: scopt.Read[DateTime] = scopt.Read.reads(x => new DateTime(x.toLong))
    val commonsConfig = new Config()
    val engines = commonsConfig.getSettingsEngines
    val parser = new scopt.OptionParser[RandomConfig]("random") {
      head("random")
      opt[Int]("appid") required () action { (x, c) =>
        c.copy(appid = x)
      } text ("the App ID that is the parent of the specified Algo ID")
      opt[Int]("engineid") required () action { (x, c) =>
        c.copy(engineid = x)
      } validate { x =>
        engines.get(x) map { _ => success } getOrElse failure(s"the Engine ID does not correspond to a valid Engine")
      } text ("the Engine ID that is the parent of the specified Algo ID")
      opt[Int]("algoid") required () action { (x, c) =>
        c.copy(algoid = x)
      } text ("the Algo ID of this run")
      opt[Int]("evalid") action { (x, c) =>
        c.copy(evalid = Some(x))
      } text ("the OfflineEval ID of this run, if any")
      opt[String]("itypes") action { (x, c) =>
        c.copy(itypes = Some(x.split(',')))
      } text ("restrict use of certain itypes (comma-separated, e.g. --itypes type1,type2)")
      opt[Int]("numPredictions") required () action { (x, c) =>
        c.copy(numPredictions = x)
      } text ("the number of predictions to generate")
      opt[Boolean]("modelSet") required () action { (x, c) =>
        c.copy(modelSet = x)
      } text ("the model set to write to after training is finished")
      opt[DateTime]("recommendationTime") required () action { (x, c) =>
        c.copy(recommendationTime = x)
      } text ("the time instant of this training (UTC UNIX timestamp in milliseconds)")
    }

    parser.parse(args, RandomConfig()) map { config =>
      val logger = Logger(Random.getClass)
      val appid = config.evalid.getOrElse(config.appid)
      val items = config.evalid map { _ => commonsConfig.getAppdataTrainingItems } getOrElse commonsConfig.getAppdataItems
      val itypes = config.itypes map { _.toSet } getOrElse Set()
      val validItems = items.getByAppid(appid).toSeq filter { item =>
        val typeValidity = config.itypes map { t => (t.toSet & item.itypes.toSet).size > 0 } getOrElse true
        val timeValidity = (item.starttime, item.endtime) match {
          case (Some(st), None) => config.recommendationTime >= st
          case (None, Some(et)) => config.recommendationTime <= et
          case (Some(st), Some(et)) => st <= config.recommendationTime && config.recommendationTime <= et
          case _ => true
        }
        typeValidity && timeValidity
      }
      logger.info(s"# valid items: ${validItems.size}")
      val randomScores = Seq.range(1, config.numPredictions + 1).reverse map { _.toDouble }
      engines.get(config.engineid).get.infoid match {
        case "itemrec" => {
          val itemRecScores = config.evalid map { _ => commonsConfig.getModeldataTrainingItemRecScores } getOrElse commonsConfig.getModeldataItemRecScores
          val users = config.evalid map { _ => commonsConfig.getAppdataTrainingUsers } getOrElse commonsConfig.getAppdataUsers
          users.getByAppid(appid) foreach { user =>
            val randomItems = scala.util.Random.shuffle(validItems).take(config.numPredictions)
            itemRecScores.insert(ItemRecScore(
              uid = user.id,
              iids = randomItems map { _.id },
              scores = randomScores,
              itypes = randomItems map { _.itypes },
              appid = appid,
              algoid = config.algoid,
              modelset = config.modelSet))
          }
        }
        case "itemsim" => {
          val itemSimScores = config.evalid map { _ => commonsConfig.getModeldataTrainingItemSimScores } getOrElse commonsConfig.getModeldataItemSimScores
          items.getByAppid(appid) foreach { item =>
            val randomItems = scala.util.Random.shuffle(validItems).take(config.numPredictions)
            itemSimScores.insert(ItemSimScore(
              iid = item.id,
              simiids = randomItems map { _.id },
              scores = randomScores,
              itypes = randomItems map { _.itypes },
              appid = appid,
              algoid = config.algoid,
              modelset = config.modelSet))
          }
        }
      }
      logger.info("Run finished")
    }
  }
}
