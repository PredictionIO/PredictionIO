package org.apache.predictionio.controller

import org.apache.predictionio.workflow.WorkflowParams
import org.scalatest.FunSuite
import org.scalatest.Inside
import org.scalatest.Matchers._
import org.scalatest.Inspectors._

import org.apache.predictionio.workflow.SharedSparkContext

class FastEngineSuite
extends FunSuite with Inside with SharedSparkContext {
  import org.apache.predictionio.controller.Engine0._
  
  test("Single Evaluation") {
    val engine = new FastEvalEngine(
      Map("" -> classOf[PDataSource2]),
      Map("" -> classOf[PPreparator1]),
      Map(
        "PAlgo2" -> classOf[PAlgo2],
        "PAlgo3" -> classOf[PAlgo3]
      ),
      Map("" -> classOf[LServing1]))

    val qn = 10
    val en = 3

    val engineParams = EngineParams(
      dataSourceParams = PDataSource2.Params(id = 0, en = en, qn = qn),
      preparatorParams = PPreparator1.Params(1),
      algorithmParamsList = Seq(
        ("PAlgo2", PAlgo2.Params(20)),
        ("PAlgo2", PAlgo2.Params(21)),
        ("PAlgo3", PAlgo3.Params(22))
      ),
      servingParams = LServing1.Params(3))

    val algoCount = engineParams.algorithmParamsList.size
    val pd = ProcessedData(1, TrainingData(0))
    val model0 = PAlgo2.Model(20, pd)
    val model1 = PAlgo2.Model(21, pd)
    val model2 = PAlgo3.Model(22, pd)

    val evalDataSet = engine.eval(sc, engineParams, WorkflowParams())

    evalDataSet should have size en

    forAll(evalDataSet.zipWithIndex) { case (evalData, ex) => {
      val (evalInfo, qpaRDD) = evalData
      evalInfo shouldBe EvalInfo(0)

      val qpaSeq: Seq[(Query, Prediction, Actual)] = qpaRDD.collect

      qpaSeq should have size qn

      forAll (qpaSeq) { case (q, p, a) => 
        val Query(qId, qEx, qQx, _) = q
        val Actual(aId, aEx, aQx) = a
        qId shouldBe aId
        qEx shouldBe ex
        aEx shouldBe ex
        qQx shouldBe aQx

        inside (p) { case Prediction(pId, pQ, pModels, pPs) => {
          pId shouldBe 3
          pQ shouldBe q
          pModels shouldBe None
          pPs should have size algoCount
          pPs shouldBe Seq(
            Prediction(id = 20, q = q, models = Some(model0)),
            Prediction(id = 21, q = q, models = Some(model1)),
            Prediction(id = 22, q = q, models = Some(model2))
          )
        }}
      }
    }}
  }

  test("Batch Evaluation") {
    val engine = new FastEvalEngine(
      Map("" -> classOf[PDataSource2]),
      Map("" -> classOf[PPreparator1]),
      Map("" -> classOf[PAlgo2]),
      Map("" -> classOf[LServing1]))

    val qn = 10
    val en = 3

    val baseEngineParams = EngineParams(
      dataSourceParams = PDataSource2.Params(id = 0, en = en, qn = qn),
      preparatorParams = PPreparator1.Params(1),
      algorithmParamsList = Seq(("", PAlgo2.Params(2))),
      servingParams = LServing1.Params(3))

    val ep0 = baseEngineParams
    val ep1 = baseEngineParams.copy(
      algorithmParamsList = Seq(("", PAlgo2.Params(2))))
    val ep2 = baseEngineParams.copy(
      algorithmParamsList = Seq(("", PAlgo2.Params(20))))

    val engineEvalDataSet = engine.batchEval(
      sc,
      Seq(ep0, ep1, ep2),
      WorkflowParams())

    val evalDataSet0 = engineEvalDataSet(0)._2
    val evalDataSet1 = engineEvalDataSet(1)._2
    val evalDataSet2 = engineEvalDataSet(2)._2

    evalDataSet0 shouldBe evalDataSet1
    evalDataSet0 should not be evalDataSet2
    evalDataSet1 should not be evalDataSet2

    // evalDataSet0._1 should be theSameInstanceAs evalDataSet1._1
    // When things are cached correctly, evalDataSet0 and 1 should share the
    // same EI
    evalDataSet0.zip(evalDataSet1).foreach { case (e0, e1) => {
      e0._1 should be theSameInstanceAs e1._1
      e0._2 should be theSameInstanceAs e1._2
    }}
   
    // So as set1 and set2, however, the QPA-RDD should be different.
    evalDataSet1.zip(evalDataSet2).foreach { case (e1, e2) => {
      e1._1 should be theSameInstanceAs e2._1
      val e1Qpa = e1._2
      val e2Qpa = e2._2
      e1Qpa should not be theSameInstanceAs (e2Qpa)
    }}
  }
  
  test("Not cached when isEqual not implemented") {
    // PDataSource3.Params is a class not case class. Need to implement the
    // isEqual function for hashing.
    val engine = new FastEvalEngine(
      Map("" -> classOf[PDataSource4]),
      Map("" -> classOf[PPreparator1]),
      Map("" -> classOf[PAlgo2]),
      Map("" -> classOf[LServing1]))

    val qn = 10
    val en = 3

    val baseEngineParams = EngineParams(
      dataSourceParams = new PDataSource4.Params(id = 0, en = en, qn = qn),
      preparatorParams = PPreparator1.Params(1),
      algorithmParamsList = Seq(("", PAlgo2.Params(2))),
      servingParams = LServing1.Params(3))

    val ep0 = baseEngineParams
    val ep1 = baseEngineParams.copy(
      algorithmParamsList = Seq(("", PAlgo2.Params(3))))
    // ep2.dataSource is different from ep0.
    val ep2 = baseEngineParams.copy(
      dataSourceParams = ("", new PDataSource4.Params(id = 0, en = en, qn = qn)),
      algorithmParamsList = Seq(("", PAlgo2.Params(3))))

    val engineEvalDataSet = engine.batchEval(
      sc,
      Seq(ep0, ep1, ep2),
      WorkflowParams())

    val evalDataSet0 = engineEvalDataSet(0)._2
    val evalDataSet1 = engineEvalDataSet(1)._2
    val evalDataSet2 = engineEvalDataSet(2)._2

    evalDataSet0 should not be evalDataSet1
    evalDataSet0 should not be evalDataSet2
    evalDataSet1 should not be evalDataSet2

    // Set0 should have same EI as Set1, since their dsp are the same instance.
    evalDataSet0.zip(evalDataSet1).foreach { case (e0, e1) => {
      e0._1 should be theSameInstanceAs (e1._1)
    }}
  
    // Set1 should have different EI as Set2, since Set2's dsp is another
    // instance
    evalDataSet1.zip(evalDataSet2).foreach { case (e1, e2) => {
      e1._1 should not be theSameInstanceAs (e2._1)
    }}
  }
}
