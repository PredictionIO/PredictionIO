package io.prediction.evaluations.commons.paramgen

import io.prediction.commons._

import grizzled.slf4j.Logger

case class ParamGenConfig(
  evalids: Seq[Int] = Seq(),
  algoid: Int = 0,
  loop: Int = 0,
  paramsets: Int = 0)

object ParamGen {
  def main(args: Array[String]) {
    val commonsConfig = new Config()
    val algos = commonsConfig.getSettingsAlgos
    val parser = new scopt.OptionParser[ParamGenConfig]("paramgen") {
      head("paramgen")
      opt[String]("evalids") required () action { (x, c) =>
        c.copy(evalids = x.split(',').map(_.toInt))
      } text ("comma-separated list of OfflineEval IDs that the parameter generator should consider")
      opt[Int]("algoid") required () action { (x, c) =>
        c.copy(algoid = x)
      } validate { x =>
        algos.get(x) map { _ => success } getOrElse failure(s"the Algo ID does not correspond to a valid Algo")
      } text ("the Algo ID of the tune subject")
      opt[Int]("loop") required () action { (x, c) =>
        c.copy(loop = x)
      } text ("the current loop number of this set of parameter")
      opt[Int]("paramsets") required () action { (x, c) =>
        c.copy(paramsets = x)
      } text ("the number of parameter sets of this tuning")
    }

    parser.parse(args, ParamGenConfig()) map { config =>
      val logger = Logger(ParamGen.getClass)

      val evalids = config.evalids
      val algoid = config.algoid
      val loop = config.loop
      val paramsets = config.paramsets
      val algo = algos.get(algoid).get

      /** Figure out what parameters can be tuned */
      val paramsToTune = algo.params.keySet filter { k =>
        algo.params.keySet.exists(e => s"${k}Min" == e) &&
          algo.params.keySet.exists(e => s"${k}Max" == e)
      }

      for (i <- 1 to paramsets) {
        /** Pick a random value between intervals */
        val paramsValues = paramsToTune map { p =>
          val minValue = algo.params(s"${p}Min")
          val maxValue = algo.params(s"${p}Max")
          algo.params(s"${p}Min") match {
            case n: Int =>
              val diff = maxValue.asInstanceOf[Int] - minValue.asInstanceOf[Int]
              if (diff == 0)
                p -> minValue.asInstanceOf[Int]
              else
                p -> (minValue.asInstanceOf[Int] + scala.util.Random.nextInt(diff))
            case n: Double => p -> (minValue.asInstanceOf[Double] + scala.util.Random.nextDouble() * (maxValue.asInstanceOf[Double] - minValue.asInstanceOf[Double]))
          }
        }
        evalids foreach { evalid =>
          val algoToTune = algo.copy(
            offlineevalid = Some(evalid.toInt),
            loop = Some(loop),
            params = algo.params ++ paramsValues.toMap,
            paramset = Some(i),
            status = "simeval")
          algos.insert(algoToTune)
        }
      }
    }
  }
}
