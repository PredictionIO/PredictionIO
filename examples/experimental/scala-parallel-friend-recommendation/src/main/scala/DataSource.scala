package org.apache.predictionio.examples.pfriendrecommendation

import org.apache.predictionio.controller.PDataSource
import org.apache.predictionio.controller.EmptyEvaluationInfo
import org.apache.predictionio.controller.EmptyActualResult
import org.apache.predictionio.controller.Params
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.Storage

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD

import grizzled.slf4j.Logger

/*
  Data Source Params : path to data file
 */
case class DataSourceParams(
  val graphEdgelistPath: String
) extends Params

case class TrainingData(
  val g:Graph[Int,Int],
  val identityMatrix:RDD[(VertexId,Double)]
)

class DataSource(val dsp: DataSourceParams)
  extends PDataSource[TrainingData, EmptyEvaluationInfo, Query, Double] {

  override
  def readTraining(sc:SparkContext) : TrainingData = {
    val g = GraphLoader.edgeListFile(sc, dsp.graphEdgelistPath)
    // In the interest of space (since we calculate at most n*n simrank scores),
    // each of the n vertices should have vertexID in the range 0 to n-1
    // val g2 = DeltaSimRankRDD.normalizeGraph(g)
    val identity = DeltaSimRankRDD.identityMatrix(sc, g.vertices.count())
    new TrainingData(g, identity)
  }
}

case class NodeSamplingDSParams(
  val graphEdgelistPath: String,
  val sampleFraction: Double
) extends Params

class NodeSamplingDataSource(val dsp: NodeSamplingDSParams)
  extends PDataSource[TrainingData, EmptyEvaluationInfo, Query, Double] {

  override
  def readTraining(sc:SparkContext) : TrainingData = {
    val g = GraphLoader.edgeListFile(sc, dsp.graphEdgelistPath)
    val sampled = Sampling.nodeSampling(sc, g, dsp.sampleFraction)
    val identity = DeltaSimRankRDD.identityMatrix(sc, g.vertices.count())
    new TrainingData(sampled, identity)
  }
}

case class FFSamplingDSParams(
  val graphEdgelistPath: String,
  val sampleFraction: Double,
  val geoParam: Double
) extends Params

class ForestFireSamplingDataSource(val dsp: FFSamplingDSParams)
  extends PDataSource[TrainingData, EmptyEvaluationInfo, Query, Double] {

  override
  def readTraining(sc:SparkContext) : TrainingData = {
    val g = GraphLoader.edgeListFile(sc, dsp.graphEdgelistPath)
    val sampled = Sampling.forestFireSamplingInduced(
      sc,
      g,
      dsp.sampleFraction,
      dsp.geoParam)

    val identity = DeltaSimRankRDD.identityMatrix(sc, g.vertices.count())
    new TrainingData(sampled, identity)
  }
}
