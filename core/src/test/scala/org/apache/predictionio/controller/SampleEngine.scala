package org.apache.predictionio.controller

import org.apache.predictionio.controller.{Params => PIOParams}
import org.apache.predictionio.core._

import grizzled.slf4j.Logger
import org.apache.predictionio.workflow.WorkflowParams
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

object Engine0 {
  @transient lazy val logger = Logger[this.type] 

  case class TrainingData(id: Int, error: Boolean = false) extends SanityCheck {
    def sanityCheck(): Unit = {
      Predef.assert(!error, "Not Error")
    }
  }

  case class EvalInfo(id: Int)
  case class ProcessedData(id: Int, td: TrainingData)

  case class Query(id: Int, ex: Int = 0, qx: Int = 0, supp: Boolean = false)
  case class Actual(id: Int, ex: Int = 0, qx: Int = 0)
  case class Prediction(
    id: Int, q: Query, models: Option[Any] = None, 
    ps: Seq[Prediction] = Seq[Prediction]())

  class PDataSource0(id: Int = 0) 
  extends PDataSource[TrainingData, EvalInfo, Query, Actual] {
    def readTraining(sc: SparkContext): TrainingData = {
      TrainingData(id)
    }
  }
  
  class PDataSource1(id: Int = 0, en: Int = 0, qn: Int = 0)
  extends PDataSource[TrainingData, EvalInfo, Query, Actual] {
    def readTraining(sc: SparkContext): TrainingData = TrainingData(id)
    
    override
    def readEval(sc: SparkContext)
    : Seq[(TrainingData, EvalInfo, RDD[(Query, Actual)])] = {
      (0 until en).map { ex => {
        val qaSeq: Seq[(Query, Actual)] = (0 until qn).map { qx => {
          (Query(id, ex=ex, qx=qx), Actual(id, ex, qx))
        }}
        (TrainingData(id), EvalInfo(id), sc.parallelize(qaSeq))
      }}
    }
  }

  object PDataSource2 {
    case class Params(id: Int, en: Int = 0, qn: Int = 0) extends PIOParams
  }
  
  class PDataSource2(params: PDataSource2.Params)
  extends PDataSource[TrainingData, EvalInfo, Query, Actual] {
    val id = params.id
    def readTraining(sc: SparkContext): TrainingData = TrainingData(id)
    
    override
    def readEval(sc: SparkContext)
    : Seq[(TrainingData, EvalInfo, RDD[(Query, Actual)])] = {
      (0 until params.en).map { ex => {
        val qaSeq: Seq[(Query, Actual)] = (0 until params.qn).map { qx => {
          (Query(id, ex=ex, qx=qx), Actual(id, ex, qx))
        }}
        (TrainingData(id), EvalInfo(id), sc.parallelize(qaSeq))
      }}
    }
  }
  
  class PDataSource3(id: Int = 0, error: Boolean = false) 
  extends PDataSource[TrainingData, EvalInfo, Query, Actual] {
    def readTraining(sc: SparkContext): TrainingData = {
      TrainingData(id = id, error = error)
    }
  }
  
  object PDataSource4 {
    class Params(val id: Int, val en: Int = 0, val qn: Int = 0) 
      extends PIOParams
  }
  
  class PDataSource4(params: PDataSource4.Params)
  extends PDataSource[TrainingData, EvalInfo, Query, Actual] {
    val id = params.id
    def readTraining(sc: SparkContext): TrainingData = TrainingData(id)
    
    override
    def readEval(sc: SparkContext)
    : Seq[(TrainingData, EvalInfo, RDD[(Query, Actual)])] = {
      (0 until params.en).map { ex => {
        val qaSeq: Seq[(Query, Actual)] = (0 until params.qn).map { qx => {
          (Query(id, ex=ex, qx=qx), Actual(id, ex, qx))
        }}
        (TrainingData(id), EvalInfo(id), sc.parallelize(qaSeq))
      }}
    }
  }
  
  class LDataSource0(id: Int, en: Int = 0, qn: Int = 0) 
    extends LDataSource[TrainingData, EvalInfo, Query, Actual] {
    def readTraining(): TrainingData = TrainingData(id)
   
    override
    def readEval()
    : Seq[(TrainingData, EvalInfo, Seq[(Query, Actual)])] = {
      (0 until en).map { ex => {
        val qaSeq: Seq[(Query, Actual)] = (0 until qn).map { qx => {
          (Query(id, ex=ex, qx=qx), Actual(id, ex, qx))
        }}
        (TrainingData(id), EvalInfo(id), qaSeq)
      }}
    }
  }
  
  object LDataSource1 {
    case class Params(id: Int, en: Int = 0, qn: Int = 0) extends PIOParams
  }
  
  class LDataSource1(params: LDataSource1.Params)
  extends LDataSource[TrainingData, EvalInfo, Query, Actual] {
    val id = params.id
    def readTraining(): TrainingData = TrainingData(id)
    
    override
    def readEval(): Seq[(TrainingData, EvalInfo, Seq[(Query, Actual)])] = {
      (0 until params.en).map { ex => {
        val qaSeq: Seq[(Query, Actual)] = (0 until params.qn).map { qx => {
          (Query(id, ex=ex, qx=qx), Actual(id, ex, qx))
        }}
        (TrainingData(id), EvalInfo(id), qaSeq)
      }}
    }
  }
  
  class PPreparator0(id: Int = 0)
  extends PPreparator[TrainingData, ProcessedData] {
    def prepare(sc: SparkContext, td: TrainingData): ProcessedData = {
      ProcessedData(id, td)
    }
  }

  object PPreparator1 {
    case class Params(id: Int  = 0) extends PIOParams
  }

  class PPreparator1(params: PPreparator1.Params)
  extends PPreparator[TrainingData, ProcessedData] {
    def prepare(sc: SparkContext, td: TrainingData): ProcessedData = {
      ProcessedData(params.id, td)
    }
  }

  class LPreparator0(id: Int = 0) 
  extends LPreparator[TrainingData, ProcessedData] {
    def prepare(td: TrainingData): ProcessedData = {
      ProcessedData(id, td)
    }
  }
  
  object LPreparator1 {
    case class Params(id: Int  = 0) extends PIOParams
  }

  class LPreparator1(params: LPreparator1.Params)
  extends LPreparator[TrainingData, ProcessedData] {
    def prepare(td: TrainingData): ProcessedData = {
      ProcessedData(params.id, td)
    }
  }

  object PAlgo0 {
    case class Model(id: Int, pd: ProcessedData)
  }

  class PAlgo0(id: Int = 0)
  extends PAlgorithm[ProcessedData, PAlgo0.Model, Query, Prediction] {
    def train(sc: SparkContext, pd: ProcessedData)
    : PAlgo0.Model = PAlgo0.Model(id, pd)

    override
    def batchPredict(m: PAlgo0.Model, qs: RDD[(Long, Query)])
    : RDD[(Long, Prediction)] = {
      qs.mapValues(q => Prediction(id, q, Some(m)))
    }
    
    def predict(m: PAlgo0.Model, q: Query): Prediction = {
      Prediction(id, q, Some(m))
    }
  }

  object PAlgo1 {
    case class Model(id: Int, pd: ProcessedData)
  }

  class PAlgo1(id: Int = 0)
  extends PAlgorithm[ProcessedData, PAlgo1.Model, Query, Prediction] {
    def train(sc: SparkContext, pd: ProcessedData)
    : PAlgo1.Model = PAlgo1.Model(id, pd)

    override
    def batchPredict(m: PAlgo1.Model, qs: RDD[(Long, Query)])
    : RDD[(Long, Prediction)] = {
      qs.mapValues(q => Prediction(id, q, Some(m)))
    }

    def predict(m: PAlgo1.Model, q: Query): Prediction = {
      Prediction(id, q, Some(m))
    }
  }
  
  object PAlgo2 {
    case class Model(id: Int, pd: ProcessedData)
    case class Params(id: Int) extends PIOParams
  }

  class PAlgo2(params: PAlgo2.Params)
  extends PAlgorithm[ProcessedData, PAlgo2.Model, Query, Prediction] {
    val id = params.id

    def train(sc: SparkContext, pd: ProcessedData)
    : PAlgo2.Model = PAlgo2.Model(id, pd)

    override
    def batchPredict(m: PAlgo2.Model, qs: RDD[(Long, Query)])
    : RDD[(Long, Prediction)] = {
      qs.mapValues(q => Prediction(id, q, Some(m)))
    }

    def predict(m: PAlgo2.Model, q: Query): Prediction = {
      Prediction(id, q, Some(m))
    }
  }
  
  object PAlgo3 {
    case class Model(id: Int, pd: ProcessedData)
    extends LocalFileSystemPersistentModel[Params]
    
    object Model extends LocalFileSystemPersistentModelLoader[Params, Model]

    case class Params(id: Int) extends PIOParams
  }

  class PAlgo3(params: PAlgo3.Params)
  extends PAlgorithm[ProcessedData, PAlgo3.Model, Query, Prediction] {
    val id = params.id

    def train(sc: SparkContext, pd: ProcessedData)
    : PAlgo3.Model = PAlgo3.Model(id, pd)

    override
    def batchPredict(m: PAlgo3.Model, qs: RDD[(Long, Query)])
    : RDD[(Long, Prediction)] = {
      qs.mapValues(q => Prediction(id, q, Some(m)))
    }

    def predict(m: PAlgo3.Model, q: Query): Prediction = {
      Prediction(id, q, Some(m))
    }
  }
  
  object LAlgo0 {
    case class Model(id: Int, pd: ProcessedData)
  }

  class LAlgo0(id: Int = 0) 
  extends LAlgorithm[ProcessedData, LAlgo0.Model, Query, Prediction] {
    def train(pd: ProcessedData): LAlgo0.Model = LAlgo0.Model(id, pd)

    def predict(m: LAlgo0.Model, q: Query): Prediction = {
      Prediction(id, q, Some(m))
    }
  }
  
  object LAlgo1 {
    case class Model(id: Int, pd: ProcessedData)
  }

  class LAlgo1(id: Int = 0) 
  extends LAlgorithm[ProcessedData, LAlgo1.Model, Query, Prediction] {
    def train(pd: ProcessedData): LAlgo1.Model = LAlgo1.Model(id, pd)
    
    def predict(m: LAlgo1.Model, q: Query): Prediction = {
      Prediction(id, q, Some(m))
    }
  }
  
  object LAlgo2 {
    case class Params(id: Int) extends PIOParams

    case class Model(id: Int, pd: ProcessedData)
    extends LocalFileSystemPersistentModel[EmptyParams]
    
    object Model extends LocalFileSystemPersistentModelLoader[EmptyParams, Model]
  }

  class LAlgo2(params: LAlgo2.Params) 
  extends LAlgorithm[ProcessedData, LAlgo2.Model, Query, Prediction] {
    def train(pd: ProcessedData): LAlgo2.Model = LAlgo2.Model(params.id, pd)
    
    def predict(m: LAlgo2.Model, q: Query): Prediction = {
      Prediction(params.id, q, Some(m))
    }
  }

  object LAlgo3 {
    case class Params(id: Int) extends PIOParams

    case class Model(id: Int, pd: ProcessedData)
  }

  class LAlgo3(params: LAlgo3.Params) 
  extends LAlgorithm[ProcessedData, LAlgo3.Model, Query, Prediction] {
    def train(pd: ProcessedData): LAlgo3.Model = LAlgo3.Model(params.id, pd)
    
    def predict(m: LAlgo3.Model, q: Query): Prediction = {
      Prediction(params.id, q, Some(m))
    }
  }

  // N : P2L. As N is in the middle of P and L.
  object NAlgo0 {
    case class Model(id: Int, pd: ProcessedData)
  }

  class NAlgo0 (id: Int = 0)
  extends P2LAlgorithm[ProcessedData, NAlgo0.Model, Query, Prediction] {
    def train(sc: SparkContext, pd: ProcessedData)
    : NAlgo0.Model = NAlgo0.Model(id, pd)
  
    def predict(m: NAlgo0.Model, q: Query): Prediction = {
      Prediction(id, q, Some(m))
    }
  }

  object NAlgo1 {
    case class Model(id: Int, pd: ProcessedData)
  }

  class NAlgo1 (id: Int = 0)
  extends P2LAlgorithm[ProcessedData, NAlgo1.Model, Query, Prediction] {
    def train(sc: SparkContext, pd: ProcessedData)
    : NAlgo1.Model = NAlgo1.Model(id, pd)
   
    def predict(m: NAlgo1.Model, q: Query): Prediction = {
      Prediction(id, q, Some(m))
    }
  }
  
  object NAlgo2 {
    case class Params(id: Int) extends PIOParams

    case class Model(id: Int, pd: ProcessedData)
    extends LocalFileSystemPersistentModel[EmptyParams]
    
    object Model extends LocalFileSystemPersistentModelLoader[EmptyParams, Model]
  }

  class NAlgo2(params: NAlgo2.Params) 
  extends P2LAlgorithm[ProcessedData, NAlgo2.Model, Query, Prediction] {
    def train(sc: SparkContext, pd: ProcessedData)
    : NAlgo2.Model = NAlgo2.Model(params.id, pd)
    
    def predict(m: NAlgo2.Model, q: Query): Prediction = {
      Prediction(params.id, q, Some(m))
    }
  }

  object NAlgo3 {
    case class Params(id: Int) extends PIOParams

    case class Model(id: Int, pd: ProcessedData)
  }

  class NAlgo3(params: NAlgo3.Params) 
  extends P2LAlgorithm[ProcessedData, NAlgo3.Model, Query, Prediction] {
    def train(sc: SparkContext, pd: ProcessedData)
    : NAlgo3.Model = NAlgo3.Model(params.id, pd)
    
    def predict(m: NAlgo3.Model, q: Query): Prediction = {
      Prediction(params.id, q, Some(m))
    }
  }

  class LServing0(id: Int = 0) extends LServing[Query, Prediction] {
    def serve(q: Query, ps: Seq[Prediction]): Prediction = {
      Prediction(id, q, ps=ps)
    }
  }

  object LServing1 {
    case class Params(id: Int) extends PIOParams
  }
  
  class LServing1(params: LServing1.Params) extends LServing[Query, Prediction] {
    def serve(q: Query, ps: Seq[Prediction]): Prediction = {
      Prediction(params.id, q, ps=ps)
    }
  }
  
  class LServing2(id: Int) extends LServing[Query, Prediction] {
    override
    def supplement(q: Query): Query = q.copy(supp = true)

    def serve(q: Query, ps: Seq[Prediction]): Prediction = {
      Prediction(id, q, ps=ps)
    }
  }
}

object Engine1 {
  case class EvalInfo(v: Double) extends Serializable
  case class Query() extends Serializable
  case class Prediction() extends Serializable
  case class Actual() extends Serializable
  case class DSP(v: Double) extends Params
}

class Engine1 
extends BaseEngine[
  Engine1.EvalInfo, Engine1.Query, Engine1.Prediction, Engine1.Actual] {

  def train(
    sc: SparkContext, 
    engineParams: EngineParams,
    engineInstanceId: String = "",
    params: WorkflowParams = WorkflowParams()): Seq[Any] = Seq[Any]()

  def eval(sc: SparkContext, engineParams: EngineParams, params: WorkflowParams)
  : Seq[(Engine1.EvalInfo, 
      RDD[(Engine1.Query, Engine1.Prediction, Engine1.Actual)])] = {
    val dsp = engineParams.dataSourceParams._2.asInstanceOf[Engine1.DSP]
    Seq(
      (Engine1.EvalInfo(dsp.v),
        sc.emptyRDD[(Engine1.Query, Engine1.Prediction, Engine1.Actual)]))
  }
}


class Metric0
extends Metric[Engine1.EvalInfo, Engine1.Query, Engine1.Prediction,
Engine1.Actual, Double] {
  override def header: String = "Metric0"

  def calculate(
    sc: SparkContext, 
    evalDataSet: Seq[(Engine1.EvalInfo, RDD[(Engine1.Query, Engine1.Prediction,
    Engine1.Actual)])]): Double = {
    evalDataSet.head._1.v
  }
}

object Metric1 {
  case class Result(c: Int, v: Double) extends Serializable
}

class Metric1
extends Metric[Engine1.EvalInfo, Engine1.Query, Engine1.Prediction,
Engine1.Actual, Metric1.Result]()(Ordering.by[Metric1.Result, Double](_.v)) {
  override def header: String = "Metric1"

  def calculate(
    sc: SparkContext, 
    evalDataSet: Seq[(Engine1.EvalInfo, RDD[(Engine1.Query, Engine1.Prediction,
    Engine1.Actual)])]): Metric1.Result = {
    Metric1.Result(0, evalDataSet.head._1.v)
  }
}

