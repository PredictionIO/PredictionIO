package io.prediction.metrics.commons.map

import io.prediction.commons.Config
import io.prediction.commons.settings.OfflineEvalResult
import io.prediction.commons.filepath.OfflineMetricFile

import scala.io.Source

import grizzled.slf4j.Logger

case class MAPAtKConfig(
  appid: Int = 0,
  engineid: Int = 0,
  evalid: Int = 0,
  metricid: Int = 0,
  algoid: Int = 0,
  iteration: Int = 0,
  splitset: String = "",
  k: Int = 0,
  goal: String = "")

/**
 * Mean Average Precision at K for Single Machine
 *
 * TODO: Eliminate use of Config object. Let scheduler handles it all.
 */
object MAPAtK {
  def main(args: Array[String]) {
    val commonsConfig = new Config()
    val engines = commonsConfig.getSettingsEngines
    val parser = new scopt.OptionParser[MAPAtKConfig]("mapatk") {
      head("mapatk")
      opt[Int]("appid") required () action { (x, c) =>
        c.copy(appid = x)
      } text ("the App ID that this metric will be applied to")
      opt[Int]("engineid") required () action { (x, c) =>
        c.copy(engineid = x)
      } validate { x =>
        engines.get(x) map { _ => success } getOrElse failure(s"the Engine ID does not correspond to a valid Engine")
      } text ("the Engine ID that this metric will be applied to")
      opt[Int]("evalid") required () action { (x, c) =>
        c.copy(evalid = x)
      } text ("the OfflineEval ID that this metric will be applied to")
      opt[Int]("metricid") required () action { (x, c) =>
        c.copy(metricid = x)
      } text ("the OfflineEvalMetric ID that this metric will be applied to")
      opt[Int]("algoid") required () action { (x, c) =>
        c.copy(algoid = x)
      } text ("the Algo ID that this metric will be applied to")
      opt[Int]("iteration") required () action { (x, c) =>
        c.copy(iteration = x)
      } text ("the iteration number (starts from 1 for the 1st iteration and then increment for later iterations)")
      opt[String]("splitset") required () action { (x, c) =>
        c.copy(splitset = x)
      } validate { x =>
        if (x == "validation" || x == "test") success else failure("--splitset must be either 'validation' or 'test'")
      } text ("the split set (validation or test) that this metric will be run against")
      opt[Int]("k") required () action { (x, c) =>
        c.copy(k = x)
      } text ("the k parameter for MAP@k")
      opt[String]("goal") required () action { (x, c) =>
        c.copy(goal = x)
      } validate { x =>
        x match {
          case "view" | "conversion" | "like" | "rate3" | "rate4" | "rate5" => success
          case _ => failure("invalid goal specified")
        }
      } text ("actions to be treated as relevant (valid values: view, conversion, like, rate3, rate4, rate5)")
    }

    parser.parse(args, MAPAtKConfig()) map { config =>
      val logger = Logger(MAPAtK.getClass)
      val u2iDb = if (config.splitset == "validation") commonsConfig.getAppdataValidationU2IActions else commonsConfig.getAppdataTestU2IActions
      val resultsDb = commonsConfig.getSettingsOfflineEvalResults
      val engine = engines.get(config.engineid).get

      // Collect relevant items for all users and items
      logger.info("Collecting relevance data...")
      val u2i = u2iDb.getAllByAppid(config.evalid).toSeq filter { u2iAction =>
        config.goal match {
          case "view" | "conversion" | "like" => u2iAction.action == config.goal
          case "rate3" => try { u2iAction.action == "rate" && u2iAction.v.get >= 3 } catch {
            case e: Exception =>
              logger.error(s"${u2iAction.uid}-${u2iAction.iid}: ${u2iAction.v} (${e.getMessage()})")
              false
          }
          case "rate4" => try { u2iAction.action == "rate" && u2iAction.v.get >= 4 } catch {
            case e: Exception =>
              logger.error(s"${u2iAction.uid}-${u2iAction.iid}: ${u2iAction.v} (${e.getMessage()})")
              false
          }
          case "rate5" => try { u2iAction.action == "rate" && u2iAction.v.get == 5 } catch {
            case e: Exception =>
              logger.error(s"${u2iAction.uid}-${u2iAction.iid}: ${u2iAction.v} (${e.getMessage()})")
              false
          }
        }
      }

      val relevantItems = u2i.groupBy(_.uid).mapValues(_.map(_.iid).toSet)
      val relevantUsers = if (engine.infoid == "itemsim") u2i.groupBy(_.iid).mapValues(_.map(_.uid).toSet) else Map[String, Set[String]]()

      logger.info(s"# users: ${relevantItems.size}")
      if (engine.infoid == "itemsim") logger.info(s"# items: ${relevantUsers.size}")

      // Read top-k list for every user
      val topKFilePath = OfflineMetricFile(
        commonsConfig.settingsLocalTempRoot,
        config.appid,
        config.engineid,
        config.evalid,
        config.metricid,
        config.algoid,
        "topKItems.tsv")
      logger.info(s"Reading top-K list from: $topKFilePath")
      val prefixSize = config.evalid.toString.length + 1

      val topKItems: Map[String, Seq[String]] = engine.infoid match {
        case "itemrec" => {
          Source.fromFile(topKFilePath).getLines().toSeq.map(
            _.split("\t")).groupBy(
              _.apply(0)) map { t =>
                (t._1.drop(prefixSize) -> t._2.apply(0).apply(1).split(",").toSeq.map(_.drop(prefixSize)))
              }
        }
        case "itemsim" => {
          /**
           * topKItems.tsv for ItemSim
           *     iid     simiid  score
           *     i0      i1      3.2
           *     i0      i4      2.5
           *     i0      i5      1.4
           *
           * 1. Read all lines into a Seq[String].
           * 2. Split by \t into Seq[Array[String]].
           * 3. Group by first element (iid) into Map[String, Seq[Array[String]]].
           * 4. Sort and filter Seq[Array[String]] to become Map[String, Seq[String]].
           */
          Source.fromFile(topKFilePath).getLines().toSeq.map(
            _.split("\t")).groupBy(
              _.apply(0)) map { t =>
                (t._1.drop(prefixSize) -> t._2.sortBy(_.apply(2)).reverse.map(_.apply(1).drop(prefixSize)))
              }
        }
      }

      logger.info(s"Running MAP@${config.k} for ${engine.infoid} engine")
      val mapAtK: Double = engine.infoid match {
        case "itemrec" => {
          val apAtK = topKItems map { t =>
            relevantItems.get(t._1) map { ri =>
              averagePrecisionAtK(config.k, t._2, ri)
            } getOrElse 0.0
          }
          apAtK.sum / scala.math.min(topKItems.size, relevantItems.size)
        }
        case "itemsim" => {
          val iapAtK = topKItems map { t =>
            relevantUsers.get(t._1) map { ru =>
              val apAtK = ru map { uid =>
                relevantItems.get(uid) map { ri =>
                  averagePrecisionAtK(config.k, t._2, ri)
                } getOrElse 0.0
              }
              apAtK.sum / scala.math.min(ru.size, relevantItems.size)
            } getOrElse 0.0
          }
          iapAtK.sum / scala.math.min(topKItems.size, relevantUsers.size)
        }
        case _ => 0.0
      }

      logger.info(s"MAP@${config.k} = $mapAtK. Saving results...")

      resultsDb.save(OfflineEvalResult(
        evalid = config.evalid,
        metricid = config.metricid,
        algoid = config.algoid,
        score = mapAtK,
        iteration = config.iteration,
        splitset = config.splitset))

      logger.info(s"MAP@${config.k} has run to completion")
    }
  }

  /**
   * Calculate the mean average precision @ k
   *
   * ap@k = sum(P(i)/min(m, k)) wher i=1 to k
   * k is number of prediction to be retrieved.
   * P(i) is the precision at position i of the top-K list
   *    if the item at position i is relevant, then P(i) = (the number of releavent items up to that position in the top-k list / position)
   *    if the item at position i is not relevant, then P(i)=0
   * m is the number of relevant items for this user.
   *
   * @return the average precision at k
   */
  def averagePrecisionAtK(k: Int, predictedItems: Seq[String], relevantItems: Set[String]): Double = {
    // supposedly the predictedItems.size should match k
    // NOTE: what if predictedItems is less than k? use the avaiable items as k.
    val n = scala.math.min(predictedItems.size, k)

    // find if each element in the predictedItems is one of the relevant items
    // if so, map to 1. else map to 0
    // (0, 1, 0, 1, 1, 0, 0)
    val relevantBinary: Seq[Int] = predictedItems.take(n).map { x => if (relevantItems(x)) 1 else 0 }
    val pAtKNom = relevantBinary.scanLeft(0)(_ + _).drop(1).zip(relevantBinary).map(t => if (t._2 != 0) t._1 else 0).map(_.toDouble)
    val pAtKDenom = 1 to relevantBinary.size
    val pAtK = pAtKNom zip pAtKDenom map { t => t._1 / t._2 }
    val apAtKDenom = scala.math.min(n, relevantItems.size)
    if (apAtKDenom == 0) 0 else pAtK.sum / apAtKDenom
  }
}
