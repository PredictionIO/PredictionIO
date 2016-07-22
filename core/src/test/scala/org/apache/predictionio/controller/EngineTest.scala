package org.apache.predictionio.controller

import org.apache.predictionio.workflow.PersistentModelManifest
import org.apache.predictionio.workflow.SharedSparkContext
import org.apache.predictionio.workflow.StopAfterPrepareInterruption
import org.apache.predictionio.workflow.StopAfterReadInterruption

import grizzled.slf4j.Logger
import org.apache.predictionio.workflow.WorkflowParams
import org.apache.spark.rdd.RDD
import org.scalatest.Inspectors._
import org.scalatest.Matchers._
import org.scalatest.FunSuite
import org.scalatest.Inside

import scala.util.Random

class EngineSuite
extends FunSuite with Inside with SharedSparkContext {
  import org.apache.predictionio.controller.Engine0._
  @transient lazy val logger = Logger[this.type] 

  test("Engine.train") {
    val engine = new Engine(
      classOf[PDataSource2],
      classOf[PPreparator1],
      Map("" -> classOf[PAlgo2]),
      classOf[LServing1])

    val engineParams = EngineParams(
      dataSourceParams = PDataSource2.Params(0),
      preparatorParams = PPreparator1.Params(1),
      algorithmParamsList = Seq(("", PAlgo2.Params(2))),
      servingParams = LServing1.Params(3))

    val models = engine.train(
      sc, 
      engineParams, 
      engineInstanceId = "",
      params = WorkflowParams())
    
    val pd = ProcessedData(1, TrainingData(0))

    // PAlgo2.Model doesn't have IPersistentModel trait implemented. Hence the
    // model extract after train is Unit.
    models should contain theSameElementsAs Seq(Unit)
  }

  test("Engine.train persisting PAlgo.Model") {
    val engine = new Engine(
      classOf[PDataSource2],
      classOf[PPreparator1],
      Map(
        "PAlgo2" -> classOf[PAlgo2],
        "PAlgo3" -> classOf[PAlgo3]
      ),
      classOf[LServing1])

    val engineParams = EngineParams(
      dataSourceParams = PDataSource2.Params(0),
      preparatorParams = PPreparator1.Params(1),
      algorithmParamsList = Seq(
        ("PAlgo2", PAlgo2.Params(2)),
        ("PAlgo3", PAlgo3.Params(21)),
        ("PAlgo3", PAlgo3.Params(22))
      ),
      servingParams = LServing1.Params(3))

    val pd = ProcessedData(1, TrainingData(0))
    val model21 = PAlgo3.Model(21, pd)
    val model22 = PAlgo3.Model(22, pd)

    val models = engine.train(
      sc, 
      engineParams, 
      engineInstanceId = "",
      params = WorkflowParams())

    val pModel21 = PersistentModelManifest(model21.getClass.getName)
    val pModel22 = PersistentModelManifest(model22.getClass.getName)
    
    models should contain theSameElementsAs Seq(Unit, pModel21, pModel22)
  }

  test("Engine.train persisting LAlgo.Model") {
    val engine = Engine(
      classOf[LDataSource1],
      classOf[LPreparator1],
      Map(
        "LAlgo1" -> classOf[LAlgo1],
        "LAlgo2" -> classOf[LAlgo2],
        "LAlgo3" -> classOf[LAlgo3]
      ),
      classOf[LServing1])

    val engineParams = EngineParams(
      dataSourceParams = LDataSource1.Params(0),
      preparatorParams = LPreparator1.Params(1),
      algorithmParamsList = Seq(
        ("LAlgo2", LAlgo2.Params(20)),
        ("LAlgo2", LAlgo2.Params(21)),
        ("LAlgo3", LAlgo3.Params(22))),
      servingParams = LServing1.Params(3))

    val pd = ProcessedData(1, TrainingData(0))
    val model20 = LAlgo2.Model(20, pd)
    val model21 = LAlgo2.Model(21, pd)
    val model22 = LAlgo3.Model(22, pd)

    //val models = engine.train(sc, engineParams, WorkflowParams())
    val models = engine.train(
      sc, 
      engineParams, 
      engineInstanceId = "",
      params = WorkflowParams())

    val pModel20 = PersistentModelManifest(model20.getClass.getName)
    val pModel21 = PersistentModelManifest(model21.getClass.getName)
    
    models should contain theSameElementsAs Seq(pModel20, pModel21, model22)
  }
  
  test("Engine.train persisting P&NAlgo.Model") {
    val engine = new Engine(
      classOf[PDataSource2],
      classOf[PPreparator1],
      Map(
        "PAlgo2" -> classOf[PAlgo2],
        "PAlgo3" -> classOf[PAlgo3],
        "NAlgo2" -> classOf[NAlgo2],
        "NAlgo3" -> classOf[NAlgo3]
      ),
      classOf[LServing1])

    val engineParams = EngineParams(
      dataSourceParams = PDataSource2.Params(0),
      preparatorParams = PPreparator1.Params(1),
      algorithmParamsList = Seq(
        ("PAlgo2", PAlgo2.Params(20)),
        ("PAlgo3", PAlgo3.Params(21)),
        ("PAlgo3", PAlgo3.Params(22)),
        ("NAlgo2", NAlgo2.Params(23)),
        ("NAlgo3", NAlgo3.Params(24)),
        ("NAlgo3", NAlgo3.Params(25))
      ),
      servingParams = LServing1.Params(3))

    val pd = ProcessedData(1, TrainingData(0))
    val model21 = PAlgo3.Model(21, pd)
    val model22 = PAlgo3.Model(22, pd)
    val model23 = NAlgo2.Model(23, pd)
    val model24 = NAlgo3.Model(24, pd)
    val model25 = NAlgo3.Model(25, pd)

    //val models = engine.train(sc, engineParams, WorkflowParams())
    val models = engine.train(
      sc, 
      engineParams, 
      engineInstanceId = "",
      params = WorkflowParams())

    val pModel21 = PersistentModelManifest(model21.getClass.getName)
    val pModel22 = PersistentModelManifest(model22.getClass.getName)
    val pModel23 = PersistentModelManifest(model23.getClass.getName)
    
    models should contain theSameElementsAs Seq(
      Unit, pModel21, pModel22, pModel23, model24, model25)
  }

  test("Engine.eval") {
    val engine = new Engine(
      classOf[PDataSource2],
      classOf[PPreparator1],
      Map("" -> classOf[PAlgo2]),
      classOf[LServing1])

    val qn = 10
    val en = 3

    val engineParams = EngineParams(
      dataSourceParams = PDataSource2.Params(id = 0, en = en, qn = qn),
      preparatorParams = PPreparator1.Params(1),
      algorithmParamsList = Seq(("", PAlgo2.Params(2))),
      servingParams = LServing1.Params(3))

    val algoCount = engineParams.algorithmParamsList.size
    val pd = ProcessedData(1, TrainingData(0))
    val model0 = PAlgo2.Model(2, pd)

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
            Prediction(id = 2, q = q, models = Some(model0)))
        }}
      }
    }}
  }

  test("Engine.prepareDeploy PAlgo") {
    val engine = new Engine(
      classOf[PDataSource2],
      classOf[PPreparator1],
      Map(
        "PAlgo2" -> classOf[PAlgo2],
        "PAlgo3" -> classOf[PAlgo3],
        "NAlgo2" -> classOf[NAlgo2],
        "NAlgo3" -> classOf[NAlgo3]
      ),
      classOf[LServing1])

    val engineParams = EngineParams(
      dataSourceParams = PDataSource2.Params(0),
      preparatorParams = PPreparator1.Params(1),
      algorithmParamsList = Seq(
        ("PAlgo2", PAlgo2.Params(20)),
        ("PAlgo3", PAlgo3.Params(21)),
        ("PAlgo3", PAlgo3.Params(22)),
        ("NAlgo2", NAlgo2.Params(23)),
        ("NAlgo3", NAlgo3.Params(24)),
        ("NAlgo3", NAlgo3.Params(25))
      ),
      servingParams = LServing1.Params(3))

    val pd = ProcessedData(1, TrainingData(0))
    val model20 = PAlgo2.Model(20, pd)
    val model21 = PAlgo3.Model(21, pd)
    val model22 = PAlgo3.Model(22, pd)
    val model23 = NAlgo2.Model(23, pd)
    val model24 = NAlgo3.Model(24, pd)
    val model25 = NAlgo3.Model(25, pd)

    val rand = new Random()

    val fakeEngineInstanceId = s"FakeInstanceId-${rand.nextLong()}"

    val persistedModels = engine.train(
      sc,
      engineParams,
      engineInstanceId = fakeEngineInstanceId,
      params = WorkflowParams()
    )

    val deployableModels = engine.prepareDeploy(
      sc,
      engineParams,
      fakeEngineInstanceId,
      persistedModels,
      params = WorkflowParams()
    )

    deployableModels should contain theSameElementsAs Seq(
      model20, model21, model22, model23, model24, model25)
  }
}

class EngineTrainSuite extends FunSuite with SharedSparkContext {
  import org.apache.predictionio.controller.Engine0._
  val defaultWorkflowParams: WorkflowParams = WorkflowParams()

  test("Parallel DS/P/Algos") {
    val models = Engine.train(
      sc,
      new PDataSource0(0),
      new PPreparator0(1),
      Seq(
        new PAlgo0(2),
        new PAlgo1(3),
        new PAlgo0(4)),
      defaultWorkflowParams
    )

    val pd = ProcessedData(1, TrainingData(0))

    models should contain theSameElementsAs Seq(
      PAlgo0.Model(2, pd), PAlgo1.Model(3, pd), PAlgo0.Model(4, pd))
  }

  test("Local DS/P/Algos") {
    val models = Engine.train(
      sc,
      new LDataSource0(0),
      new LPreparator0(1),
      Seq(
        new LAlgo0(2),
        new LAlgo1(3),
        new LAlgo0(4)),
      defaultWorkflowParams
    )
    
    val pd = ProcessedData(1, TrainingData(0))

    val expectedResults = Seq(
      LAlgo0.Model(2, pd),
      LAlgo1.Model(3, pd),
      LAlgo0.Model(4, pd))

    forAll(models.zip(expectedResults)) { case (model, expected) => 
      model shouldBe a [RDD[_]]
      val localModel = model.asInstanceOf[RDD[_]].collect
      localModel should contain theSameElementsAs Seq(expected)
    }
  }

  test("P2L DS/P/Algos") {
    val models = Engine.train(
      sc,
      new PDataSource0(0),
      new PPreparator0(1),
      Seq(
        new NAlgo0(2),
        new NAlgo1(3),
        new NAlgo0(4)),
      defaultWorkflowParams
    )

    val pd = ProcessedData(1, TrainingData(0))
    
    models should contain theSameElementsAs Seq(
      NAlgo0.Model(2, pd), NAlgo1.Model(3, pd), NAlgo0.Model(4, pd))
  }
  
  test("Parallel DS/P/Algos Stop-After-Read") {
    val workflowParams = defaultWorkflowParams.copy(
      stopAfterRead = true)

    an [StopAfterReadInterruption] should be thrownBy Engine.train(
      sc,
      new PDataSource0(0),
      new PPreparator0(1),
      Seq(
        new PAlgo0(2),
        new PAlgo1(3),
        new PAlgo0(4)),
      workflowParams
    )
  }
  
  test("Parallel DS/P/Algos Stop-After-Prepare") {
    val workflowParams = defaultWorkflowParams.copy(
      stopAfterPrepare = true)

    an [StopAfterPrepareInterruption] should be thrownBy Engine.train(
      sc,
      new PDataSource0(0),
      new PPreparator0(1),
      Seq(
        new PAlgo0(2),
        new PAlgo1(3),
        new PAlgo0(4)),
      workflowParams
    )
  }
  
  test("Parallel DS/P/Algos Dirty TrainingData") {
    val workflowParams = defaultWorkflowParams.copy(
      skipSanityCheck = false)

    an [AssertionError] should be thrownBy Engine.train(
      sc,
      new PDataSource3(0, error = true),
      new PPreparator0(1),
      Seq(
        new PAlgo0(2),
        new PAlgo1(3),
        new PAlgo0(4)),
      workflowParams
    )
  }
  
  test("Parallel DS/P/Algos Dirty TrainingData But Skip Check") {
    val workflowParams = defaultWorkflowParams.copy(
      skipSanityCheck = true)

    val models = Engine.train(
      sc,
      new PDataSource3(0, error = true),
      new PPreparator0(1),
      Seq(
        new PAlgo0(2),
        new PAlgo1(3),
        new PAlgo0(4)),
      workflowParams
    )
    
  val pd = ProcessedData(1, TrainingData(0, error = true))

    models should contain theSameElementsAs Seq(
      PAlgo0.Model(2, pd), PAlgo1.Model(3, pd), PAlgo0.Model(4, pd))
  }
}


class EngineEvalSuite
extends FunSuite with Inside with SharedSparkContext {
  import org.apache.predictionio.controller.Engine0._

  @transient lazy val logger = Logger[this.type] 
  
  test("Simple Parallel DS/P/A/S") {
    val en = 2
    val qn = 5

    val evalDataSet: Seq[(EvalInfo, RDD[(Query, Prediction, Actual)])] = 
    Engine.eval(
      sc,
      new PDataSource1(id = 1, en = en, qn = qn),
      new PPreparator0(id = 2),
      Seq(new PAlgo0(id = 3)),
      new LServing0(id = 10))

    val pd = ProcessedData(2, TrainingData(1))
    val model0 = PAlgo0.Model(3, pd)

    forAll(evalDataSet.zipWithIndex) { case (evalData, ex) => {
      val (evalInfo, qpaRDD) = evalData
      evalInfo shouldBe EvalInfo(1)

      val qpaSeq: Seq[(Query, Prediction, Actual)] = qpaRDD.collect
      forAll (qpaSeq) { case (q, p, a) => 
        val Query(qId, qEx, qQx, _) = q
        val Actual(aId, aEx, aQx) = a
        qId shouldBe aId
        qEx shouldBe ex
        aEx shouldBe ex
        qQx shouldBe aQx

        inside (p) { case Prediction(pId, pQ, pModels, pPs) => {
          pId shouldBe 10
          pQ shouldBe q
          pModels shouldBe None
          pPs should have size 1
          pPs shouldBe Seq(
            Prediction(id = 3, q = q, models = Some(model0)))
        }}
      }

    }}

  }

  test("Parallel DS/P/A/S") {
    val en = 2
    val qn = 5

    val evalDataSet: Seq[(EvalInfo, RDD[(Query, Prediction, Actual)])] = 
    Engine.eval(
      sc,
      new PDataSource1(id = 1, en = en, qn = qn),
      new PPreparator0(id = 2),
      Seq(
        new PAlgo0(id = 3), 
        new PAlgo1(id = 4),
        new NAlgo1(id = 5)),
      new LServing0(id = 10))

    val pd = ProcessedData(2, TrainingData(1))
    val model0 = PAlgo0.Model(3, pd)
    val model1 = PAlgo1.Model(4, pd)
    val model2 = NAlgo1.Model(5, pd)

    forAll(evalDataSet.zipWithIndex) { case (evalData, ex) => {
      val (evalInfo, qpaRDD) = evalData
      evalInfo shouldBe EvalInfo(1)

      val qpaSeq: Seq[(Query, Prediction, Actual)] = qpaRDD.collect
      forAll (qpaSeq) { case (q, p, a) => 
        val Query(qId, qEx, qQx, _) = q
        val Actual(aId, aEx, aQx) = a
        qId shouldBe aId
        qEx shouldBe ex
        aEx shouldBe ex
        qQx shouldBe aQx

        inside (p) { case Prediction(pId, pQ, pModels, pPs) => {
          pId shouldBe 10
          pQ shouldBe q
          pModels shouldBe None
          pPs should have size 3
          pPs shouldBe Seq(
            Prediction(id = 3, q = q, models = Some(model0)),
            Prediction(id = 4, q = q, models = Some(model1)),
            Prediction(id = 5, q = q, models = Some(model2))
          )
        }}
      }
    }}
  }
  
  test("Parallel DS/P/A/S with Supplemented Query") {
    val en = 2
    val qn = 5

    val evalDataSet: Seq[(EvalInfo, RDD[(Query, Prediction, Actual)])] = 
    Engine.eval(
      sc,
      new PDataSource1(id = 1, en = en, qn = qn),
      new PPreparator0(id = 2),
      Seq(
        new PAlgo0(id = 3), 
        new PAlgo1(id = 4),
        new NAlgo1(id = 5)),
      new LServing2(id = 10))

    val pd = ProcessedData(2, TrainingData(1))
    val model0 = PAlgo0.Model(3, pd)
    val model1 = PAlgo1.Model(4, pd)
    val model2 = NAlgo1.Model(5, pd)

    forAll(evalDataSet.zipWithIndex) { case (evalData, ex) => {
      val (evalInfo, qpaRDD) = evalData
      evalInfo shouldBe EvalInfo(1)

      val qpaSeq: Seq[(Query, Prediction, Actual)] = qpaRDD.collect
      forAll (qpaSeq) { case (q, p, a) => 
        val Query(qId, qEx, qQx, qSupp) = q
        val Actual(aId, aEx, aQx) = a
        qId shouldBe aId
        qEx shouldBe ex
        aEx shouldBe ex
        qQx shouldBe aQx
        qSupp shouldBe false

        inside (p) { case Prediction(pId, pQ, pModels, pPs) => {
          pId shouldBe 10
          pQ shouldBe q
          pModels shouldBe None
          pPs should have size 3
          // queries inside prediction should have supp set to true, since it
          // represents what the algorithms see.
          val qSupp = q.copy(supp = true)
          pPs shouldBe Seq(
            Prediction(id = 3, q = qSupp, models = Some(model0)),
            Prediction(id = 4, q = qSupp, models = Some(model1)),
            Prediction(id = 5, q = qSupp, models = Some(model2))
          )
        }}
      }
    }}
  }
  
  test("Local DS/P/A/S") {
    val en = 2
    val qn = 5

    val evalDataSet: Seq[(EvalInfo, RDD[(Query, Prediction, Actual)])] = 
    Engine.eval(
      sc,
      new LDataSource0(id = 1, en = en, qn = qn),
      new LPreparator0(id = 2),
      Seq(
        new LAlgo0(id = 3), 
        new LAlgo1(id = 4),
        new LAlgo1(id = 5)),
      new LServing0(id = 10))

    val pd = ProcessedData(2, TrainingData(1))
    val model0 = LAlgo0.Model(3, pd)
    val model1 = LAlgo1.Model(4, pd)
    val model2 = LAlgo1.Model(5, pd)

    forAll(evalDataSet.zipWithIndex) { case (evalData, ex) => {
      val (evalInfo, qpaRDD) = evalData
      evalInfo shouldBe EvalInfo(1)

      val qpaSeq: Seq[(Query, Prediction, Actual)] = qpaRDD.collect
      forAll (qpaSeq) { case (q, p, a) => 
        val Query(qId, qEx, qQx, _) = q
        val Actual(aId, aEx, aQx) = a
        qId shouldBe aId
        qEx shouldBe ex
        aEx shouldBe ex
        qQx shouldBe aQx

        inside (p) { case Prediction(pId, pQ, pModels, pPs) => {
          pId shouldBe 10
          pQ shouldBe q
          pModels shouldBe None
          pPs should have size 3
          pPs shouldBe Seq(
            Prediction(id = 3, q = q, models = Some(model0)),
            Prediction(id = 4, q = q, models = Some(model1)),
            Prediction(id = 5, q = q, models = Some(model2))
          )
        }}
      }

    }}

  }
}


