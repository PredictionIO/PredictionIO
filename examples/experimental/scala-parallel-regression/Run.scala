package org.apache.predictionio.examples.regression.parallel

import org.apache.predictionio.controller.Engine
import org.apache.predictionio.controller.Params
import org.apache.predictionio.controller.PDataSource
import org.apache.predictionio.controller.P2LAlgorithm
import org.apache.predictionio.controller.IdentityPreparator
import org.apache.predictionio.controller.IEngineFactory
import org.apache.predictionio.controller.LAverageServing
import org.apache.predictionio.controller.MeanSquareError
import org.apache.predictionio.controller.Utils
import org.apache.predictionio.controller.Workflow
import org.apache.predictionio.controller.WorkflowParams

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.mllib.linalg.DenseVector
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.regression.LinearRegressionWithSGD
import org.apache.spark.mllib.regression.RegressionModel
import org.apache.spark.mllib.util.MLUtils
import org.apache.spark.rdd.RDD
import org.json4s._
import java.io.File

case class DataSourceParams(
  val filepath: String, val k: Int = 3, val seed: Int = 9527)
extends Params

case class ParallelDataSource(val dsp: DataSourceParams)
  extends PDataSource[
      DataSourceParams, Integer,
      RDD[LabeledPoint], Vector, Double] {
  override
  def read(sc: SparkContext)
  : Seq[(Integer, RDD[LabeledPoint], RDD[(Vector, Double)])] = {
    val input = sc.textFile(dsp.filepath)
    val points = input.map { line =>
      val parts = line.split(' ').map(_.toDouble)
      LabeledPoint(parts(0), Vectors.dense(parts.drop(1)))
    }

    MLUtils.kFold(points, dsp.k, dsp.seed)
    .zipWithIndex
    .map { case (dataSet, index) =>
      (Int.box(index), dataSet._1, dataSet._2.map(p => (p.features, p.label)))
    }
  }
}

case class AlgorithmParams(
  val numIterations: Int = 200, val stepSize: Double = 0.1) extends Params

case class ParallelSGDAlgorithm(val ap: AlgorithmParams)
  extends P2LAlgorithm[
      AlgorithmParams, RDD[LabeledPoint], RegressionModel, Vector, Double] {

  def train(data: RDD[LabeledPoint]): RegressionModel = {
    LinearRegressionWithSGD.train(data, ap.numIterations, ap.stepSize)
  }

  def predict(model: RegressionModel, feature: Vector): Double = {
    model.predict(feature)
  }

  @transient override lazy val querySerializer =
    Utils.json4sDefaultFormats + new VectorSerializer
}

object RegressionEngineFactory extends IEngineFactory {
  def apply() = {
    new Engine(
      classOf[ParallelDataSource],
      classOf[IdentityPreparator[RDD[LabeledPoint]]],
      Map("SGD" -> classOf[ParallelSGDAlgorithm]),
      LAverageServing(classOf[ParallelSGDAlgorithm]))
  }
}

object Run {
  def main(args: Array[String]) {
    val filepath = new File("../data/lr_data.txt").getCanonicalPath
    val dataSourceParams = DataSourceParams(filepath, 3)
    val SGD = "SGD"
    val algorithmParamsList = Seq(
      (SGD, AlgorithmParams(stepSize = 0.1)),
      (SGD, AlgorithmParams(stepSize = 0.2)),
      (SGD, AlgorithmParams(stepSize = 0.4)))

    Workflow.run(
        dataSourceClassOpt = Some(classOf[ParallelDataSource]),
        dataSourceParams = dataSourceParams,
        preparatorClassOpt =
          Some(classOf[IdentityPreparator[RDD[LabeledPoint]]]),
        algorithmClassMapOpt = Some(Map(SGD -> classOf[ParallelSGDAlgorithm])),
        algorithmParamsList = algorithmParamsList,
        servingClassOpt = Some(LAverageServing(classOf[ParallelSGDAlgorithm])),
        evaluatorClassOpt = Some(classOf[MeanSquareError]),
        params = WorkflowParams(
          batch = "Imagine: Parallel Regression"))
  }
}

class VectorSerializer extends CustomSerializer[Vector](format => (
  {
    case JArray(x) =>
      val v = x.toArray.map { y =>
        y match {
          case JDouble(z) => z
        }
      }
      new DenseVector(v)
  },
  {
    case x: Vector =>
      JArray(x.toArray.toList.map(d => JDouble(d)))
  }
))
