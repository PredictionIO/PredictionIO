package org.apache.predictionio.workflow

import org.apache.predictionio.controller._

import org.scalatest.FunSuite
import org.scalatest.Matchers._

class EvaluationWorkflowSuite extends FunSuite with SharedSparkContext {

  test("Evaluation return best engine params, simple result type: Double") {
    val engine = new Engine1()
    val ep0 = EngineParams(dataSourceParams = Engine1.DSP(0.2))
    val ep1 = EngineParams(dataSourceParams = Engine1.DSP(0.3))
    val ep2 = EngineParams(dataSourceParams = Engine1.DSP(0.3))
    val ep3 = EngineParams(dataSourceParams = Engine1.DSP(-0.2))
    val engineParamsList = Seq(ep0, ep1, ep2, ep3)

    val evaluator = MetricEvaluator(new Metric0())
  
    object Eval extends Evaluation {
      engineEvaluator = (new Engine1(), MetricEvaluator(new Metric0()))
    }

    val result = EvaluationWorkflow.runEvaluation(
      sc,
      Eval,
      engine,
      engineParamsList,
      evaluator,
      WorkflowParams())

    result.bestScore.score shouldBe 0.3
    result.bestEngineParams shouldBe ep1
  }

  test("Evaluation return best engine params, complex result type") {
    val engine = new Engine1()
    val ep0 = EngineParams(dataSourceParams = Engine1.DSP(0.2))
    val ep1 = EngineParams(dataSourceParams = Engine1.DSP(0.3))
    val ep2 = EngineParams(dataSourceParams = Engine1.DSP(0.3))
    val ep3 = EngineParams(dataSourceParams = Engine1.DSP(-0.2))
    val engineParamsList = Seq(ep0, ep1, ep2, ep3)

    val evaluator = MetricEvaluator(new Metric1())
    
    object Eval extends Evaluation {
      engineEvaluator = (new Engine1(), MetricEvaluator(new Metric1()))
    }

    val result = EvaluationWorkflow.runEvaluation(
      sc,
      Eval,
      engine,
      engineParamsList,
      evaluator,
      WorkflowParams())
  
    result.bestScore.score shouldBe Metric1.Result(0, 0.3)
    result.bestEngineParams shouldBe ep1
  }
}
