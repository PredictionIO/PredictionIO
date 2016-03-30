package pio.refactor

import io.prediction.controller.IEngineFactory
import io.prediction.controller.Engine
import io.prediction.controller._
//import io.prediction.workflow.CoreWorkflow
import grizzled.slf4j.Logger

case class Query(q: Int)

case class PredictedResult(p: Int)

case class ActualResult()

object VanillaEngine extends IEngineFactory {
  def apply() = {
    new Engine(
      classOf[DataSource],
      //classOf[Preparator],
      PIdentityPreparator(classOf[DataSource]),
      Map("algo" -> classOf[Algorithm]),
      classOf[Serving])
  }
}

object Runner {
  @transient lazy val logger = Logger[this.type]

  def main(args: Array[String]) {
    val engine = VanillaEngine()
    val engineParams = EngineParams(
      algorithmParamsList = Seq(("algo", AlgorithmParams(2)))
    )

    logger.error("Runner. before evaluation!!!")
    val evaluator = new VanillaEvaluator() 
    
    logger.error("Runner before runEval!!!")
    Workflow.runEval(
      engine = engine,
      engineParams = engineParams,
      evaluator = evaluator,
      evaluatorParams = EmptyParams())

  }
}
