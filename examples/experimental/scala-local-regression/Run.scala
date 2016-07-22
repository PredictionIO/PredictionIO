package org.apache.predictionio.examples.regression.local

import org.apache.predictionio.controller.EmptyParams
import org.apache.predictionio.controller.Engine
import org.apache.predictionio.controller.IEngineFactory
import org.apache.predictionio.controller.EngineParams
import org.apache.predictionio.controller.LFirstServing
import org.apache.predictionio.controller.LAlgorithm
import org.apache.predictionio.controller.LDataSource
import org.apache.predictionio.controller.LPreparator
import org.apache.predictionio.controller.MeanSquareError
import org.apache.predictionio.controller.Params
import org.apache.predictionio.controller.Utils
import org.apache.predictionio.controller.Workflow
import org.apache.predictionio.controller.WorkflowParams

import breeze.linalg.DenseMatrix
import breeze.linalg.DenseVector
import breeze.linalg.inv
import nak.regress.LinearRegression
import org.json4s._

import scala.io.Source
import java.io.File

case class DataSourceParams(val filepath: String, val seed: Int = 9527)
  extends Params

case class TrainingData(x: Vector[Vector[Double]], y: Vector[Double]) {
  val r = x.length
  val c = x.head.length
}

case class LocalDataSource(val dsp: DataSourceParams)
  extends LDataSource[
    DataSourceParams, String, TrainingData, Vector[Double], Double] {
  override
  def read(): Seq[(String, TrainingData, Seq[(Vector[Double], Double)])] = {
    val lines = Source.fromFile(dsp.filepath).getLines
      .toSeq.map(_.split(" ", 2))

    // FIXME: Use different training / testing data.
    val x = lines.map{ _(1).split(' ').map{_.toDouble} }.map{ e => Vector(e:_*)}
    val y = lines.map{ _(0).toDouble }

    val td = TrainingData(Vector(x:_*), Vector(y:_*))

    val oneData = ("The One", td, x.zip(y))
    return Seq(oneData)
  }
}

// When n = 0, don't drop data
// When n > 0, drop data when index mod n == k
case class PreparatorParams(n: Int = 0, k: Int = 0) extends Params

case class LocalPreparator(val pp: PreparatorParams = PreparatorParams())
  extends LPreparator[PreparatorParams, TrainingData, TrainingData] {
  def prepare(td: TrainingData): TrainingData = {
    val xyi: Vector[(Vector[Double], Double)] = td.x.zip(td.y)
      .zipWithIndex
      .filter{ e => (e._2 % pp.n) != pp.k}
      .map{ e => (e._1._1, e._1._2) }
    TrainingData(xyi.map(_._1), xyi.map(_._2))
  }
}

case class LocalAlgorithm()
  extends LAlgorithm[
      EmptyParams, TrainingData, Array[Double], Vector[Double], Double] {

  def train(td: TrainingData): Array[Double] = {
    val xArray: Array[Double] = td.x.foldLeft(Vector[Double]())(_ ++ _).toArray
    // DenseMatrix.create fills first column, then second.
    val m = DenseMatrix.create[Double](td.c, td.r, xArray).t
    val y = DenseVector[Double](td.y.toArray)
    val result = LinearRegression.regress(m, y)
    return result.data.toArray
  }

  def predict(model: Array[Double], query: Vector[Double]) = {
    model.zip(query).map(e => e._1 * e._2).sum
  }

  @transient override lazy val querySerializer =
    Utils.json4sDefaultFormats + new VectorSerializer
}

class VectorSerializer extends CustomSerializer[Vector[Double]](format => (
  {
    case JArray(s) =>
      s.map {
        case JDouble(x) => x
        case _ => 0
      }.toVector
  },
  {
    case x: Vector[Double] =>
      JArray(x.toList.map(y => JDouble(y)))
  }
))

object RegressionEngineFactory extends IEngineFactory {
  def apply() = {
    new Engine(
      classOf[LocalDataSource],
      classOf[LocalPreparator],
      Map("" -> classOf[LocalAlgorithm]),
      classOf[LFirstServing[Vector[Double], Double]])
  }
}

object Run {
  val workflowParams = WorkflowParams(
    batch = "Imagine: Local Regression",
    verbose = 3,
    saveModel = true)

  def runComponents() {
    val filepath = new File("../data/lr_data.txt").getCanonicalPath
    val dataSourceParams = new DataSourceParams(filepath)
    val preparatorParams = new PreparatorParams(n = 2, k = 0)

    Workflow.run(
      params = workflowParams,
      dataSourceClassOpt = Some(classOf[LocalDataSource]),
      dataSourceParams = dataSourceParams,
      preparatorClassOpt = Some(classOf[LocalPreparator]),
      preparatorParams = preparatorParams,
      algorithmClassMapOpt = Some(Map("" -> classOf[LocalAlgorithm])),
      algorithmParamsList = Seq(
        ("", EmptyParams())),
      servingClassOpt = Some(classOf[LFirstServing[Vector[Double], Double]]),
      evaluatorClassOpt = Some(classOf[MeanSquareError]))
  }

  def runEngine() {
    val filepath = new File("../data/lr_data.txt").getCanonicalPath
    val engine = RegressionEngineFactory()
    val engineParams = new EngineParams(
      dataSourceParams = DataSourceParams(filepath),
      preparatorParams = PreparatorParams(n = 2, k = 0),
      algorithmParamsList = Seq(("", EmptyParams())))

    Workflow.runEngine(
      params = workflowParams,
      engine = engine,
      engineParams = engineParams,
      evaluatorClassOpt = Some(classOf[MeanSquareError]))
  }

  def main(args: Array[String]) {
    runEngine()
  }
}
