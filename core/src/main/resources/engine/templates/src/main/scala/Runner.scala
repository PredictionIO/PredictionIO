package myengine

import io.prediction.PIORunner
import io.prediction.BaseEngine
import io.prediction.DefaultServer

object MyRun {
  def main(args: Array[String]): Unit = {
    val evalParams = new EvaluationParams()

    val algoParams = new MyAlgoParams()
    val serverParams = new MyServerParams()

    val engine = new BaseEngine(
      classOf[MyPreparator],
      Map("myalgo" -> classOf[MyAlgo]),
      classOf[DefaultServer[Feature, Prediction]])

    val evaluator = new MyEvaluator
    val preparator = new MyPreparator

    val algoParamsSet = Seq(
      ("myalgo", algoParams),
      ("myalgo", new MyAlgoParams()))

    PIORunner(
      evalParams,
      algoParamsSet(0),
      serverParams,
      engine,
      evaluator,
      preparator)

    PIORunner(
      evalParams,
      algoParamsSet(1),
      serverParams,
      engine,
      evaluator,
      preparator)
  }
}
