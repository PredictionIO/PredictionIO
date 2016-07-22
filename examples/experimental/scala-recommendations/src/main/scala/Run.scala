package org.apache.spark.mllib.recommendation.engine

import org.apache.predictionio.controller.Engine
import org.apache.predictionio.controller.IEngineFactory
import org.apache.predictionio.controller.IPersistentModel
import org.apache.predictionio.controller.IPersistentModelLoader
import org.apache.predictionio.controller.PDataSource
import org.apache.predictionio.controller.Params
import org.apache.predictionio.controller.PAlgorithm
import org.apache.predictionio.controller.PIdentityPreparator
import org.apache.predictionio.controller.LFirstServing
import org.apache.predictionio.controller.Utils
import org.apache.predictionio.controller.Workflow
import org.apache.predictionio.controller.WorkflowParams

import org.apache.commons.io.FileUtils
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.recommendation.ALS
import org.apache.spark.mllib.recommendation.Rating
import org.apache.spark.mllib.recommendation.MatrixFactorizationModel
import org.json4s._

import scala.io.Source

import java.io.File

case class DataSourceParams(val filepath: String) extends Params

case class DataSource(val dsp: DataSourceParams)
  extends PDataSource[DataSourceParams, Null, RDD[Rating], (Int, Int), Double] {

  override
  def read(sc: SparkContext)
  : Seq[(Null, RDD[Rating], RDD[((Int, Int), Double)])] = {
    val data = sc.textFile(dsp.filepath)
    val ratings: RDD[Rating] = data.map(_.split("::") match {
      case Array(user, item, rate) =>
        Rating(user.toInt, item.toInt, rate.toDouble)
    })

    val featureTargets: RDD[((Int, Int), Double)] = ratings.map {
      case Rating(user, product, rate) => ((user, product), rate)
    }

    Seq((null, ratings, featureTargets))
  }
}

case class AlgorithmParams(
  val rank: Int = 10,
  val numIterations: Int = 20,
  val lambda: Double = 0.01,
  val persistModel: Boolean = false) extends Params

class PMatrixFactorizationModel(rank: Int,
    userFeatures: RDD[(Int, Array[Double])],
    productFeatures: RDD[(Int, Array[Double])])
  extends MatrixFactorizationModel(rank, userFeatures, productFeatures)
  with IPersistentModel[AlgorithmParams] {
  def save(id: String, params: AlgorithmParams, sc: SparkContext): Boolean = {
    if (params.persistModel) {
      sc.parallelize(Seq(rank)).saveAsObjectFile(s"/tmp/${id}/rank")
      userFeatures.saveAsObjectFile(s"/tmp/${id}/userFeatures")
      productFeatures.saveAsObjectFile(s"/tmp/${id}/productFeatures")
    }
    params.persistModel
  }
}

object PMatrixFactorizationModel
  extends IPersistentModelLoader[AlgorithmParams, PMatrixFactorizationModel] {
  def apply(id: String, params: AlgorithmParams, sc: Option[SparkContext]) = {
    new PMatrixFactorizationModel(
      rank = sc.get.objectFile[Int](s"/tmp/${id}/rank").first,
      userFeatures = sc.get.objectFile(s"/tmp/${id}/userFeatures"),
      productFeatures = sc.get.objectFile(s"/tmp/${id}/productFeatures"))
  }
}

class ALSAlgorithm(val ap: AlgorithmParams)
  extends PAlgorithm[AlgorithmParams, RDD[Rating],
      PMatrixFactorizationModel, (Int, Int), Double] {

  def train(data: RDD[Rating]): PMatrixFactorizationModel = {
    val m = ALS.train(data, ap.rank, ap.numIterations, ap.lambda)
    new PMatrixFactorizationModel(
      rank = m.rank,
      userFeatures = m.userFeatures,
      productFeatures = m.productFeatures)
  }

  override
  def batchPredict(
    model: PMatrixFactorizationModel,
    feature: RDD[(Long, (Int, Int))]): RDD[(Long, Double)] = {
    val indexlessFeature = feature.values

    val prediction: RDD[Rating] = model.predict(indexlessFeature)

    val p: RDD[((Int, Int), Double)] = prediction.map {
      r => ((r.user, r.product), r.rating)
    }

    feature.map{ _.swap }
    .join(p)
    .map { case (up, (fi, r)) => (fi,r) }
  }

  def predict(
    model: PMatrixFactorizationModel, feature: (Int, Int)): Double = {
    model.predict(feature._1, feature._2)
  }

  @transient override lazy val querySerializer =
    Utils.json4sDefaultFormats + new Tuple2IntSerializer
}

object Run {
  def main(args: Array[String]) {
    val dsp = DataSourceParams("data/movielens.txt")
    val ap = AlgorithmParams()

    Workflow.run(
      dataSourceClassOpt = Some(classOf[DataSource]),
      dataSourceParams = dsp,
      preparatorClassOpt = Some(PIdentityPreparator(classOf[DataSource])),
      algorithmClassMapOpt = Some(Map("" -> classOf[ALSAlgorithm])),
      algorithmParamsList = Seq(("", ap)),
      servingClassOpt = Some(LFirstServing(classOf[ALSAlgorithm])),
      params = WorkflowParams(
	batch = "Imagine: P Recommendations",
        verbose = 1
      )
    )
  }
}

object RecommendationEngine extends IEngineFactory {
  def apply() = {
    new Engine(
      classOf[DataSource],
      PIdentityPreparator(classOf[DataSource]),
      Map("" -> classOf[ALSAlgorithm]),
      LFirstServing(classOf[ALSAlgorithm]))
  }
}


class Tuple2IntSerializer extends CustomSerializer[(Int, Int)](format => (
  {
    case JArray(List(JInt(x), JInt(y))) => (x.intValue, y.intValue)
  },
  {
    case x: (Int, Int) => JArray(List(JInt(x._1), JInt(x._2)))
  }
))
