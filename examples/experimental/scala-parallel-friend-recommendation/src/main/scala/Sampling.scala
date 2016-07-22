package org.apache.predictionio.examples.pfriendrecommendation

import org.apache.spark.SparkContext._
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD
import scala.collection.mutable.{ListBuffer, ArraySeq, HashSet}
import scala.util.Random

import scala.collection.mutable.HashSet
import scala.collection.mutable.Queue
import org.apache.spark.SparkContext
import scala.collection.mutable.Map

object Sampling {

  def geometricSample(param: Double) : Int = {
    var num = 1
    while(Random.nextDouble <= param) {
      num += 1
    }
    num
  }

  def sortBySrc(a:Array[Edge[Int]]): Array[Edge[Int]] = {
    if (a.length < 2) {
      a
    } else {
      val pivot = a(a.length / 2).srcId
      // 'L'ess, 'E'qual, 'G'reater
      val partitions = a.groupBy( (e:Edge[Int]) => {
        if (e.srcId < pivot)
          'L'
        else if (e.srcId > pivot)
          'G'
        else
          'E'
      })

      var sortedAccumulator: Array[Edge[Int]] = Array()
      List('L', 'E', 'G').foreach((c:Char) => {
        if (partitions.contains(c)) {
          sortedAccumulator = sortedAccumulator ++ partitions(c)
        }
      })
      sortedAccumulator
    }
  }

  // Samples vertices by forest fire random process and induces edges.
  // Fraction denotes fraction of total graph vertices to sample and geoParam
  // denotes the parameter for geometric distribution, which is used to
  // determine branching factor at each iteration of forest fire process.
  def forestFireSamplingInduced (
    sc: SparkContext,
    graph: Graph[Int,Int],
    fraction: Double,
    geoParam: Double = 0.7) =
  {
    var g = graph
    var e = sortBySrc(g.edges.toArray)
    val targetVertexCount = (graph.vertices.count() * fraction).toInt
    var seedVertices = graph.vertices
      .sample(false, fraction, targetVertexCount)
      .toArray.iterator
    var sampledVertices: HashSet[VertexId] = HashSet()
    var burnQueue: Queue[VertexId] = Queue()

    while (sampledVertices.size < targetVertexCount) {
      val seedVertex = seedVertices.next
      sampledVertices += seedVertex._1
      burnQueue += seedVertex._1
      while (burnQueue.size > 0 ){
        val vertexId = burnQueue.dequeue()
        val numToSample = geometricSample(geoParam)
        val edgeCandidates = accumulateEdges(e, vertexId)
        val burnCandidate = sc.parallelize(edgeCandidates)
          .filter((e:Edge[Int]) => {
            !sampledVertices.contains(e.dstId)
          })
        val burnFraction = numToSample.toDouble / burnCandidate.count.toDouble
        val burnEdges = burnCandidate.sample(
          false,
          burnFraction,
          Random.nextLong)
        val neighborVertexIds = burnEdges.map((e:Edge[Int]) => e.dstId)
        sampledVertices = sampledVertices ++ neighborVertexIds.toArray
        burnQueue = burnQueue ++ neighborVertexIds.toArray

        if (sampledVertices.size > targetVertexCount) {
          burnQueue.dequeueAll((v:VertexId) => true)
        }
      }
    }
    val vertex: Seq[(VertexId, Int)] = sampledVertices.map(v => (v,1))
      .toSeq
    val edges = graph.edges.filter(e =>
        sampledVertices.contains(e.srcId) && sampledVertices.contains(e.dstId)
      )
    Graph(sc.parallelize(vertex), edges)
  }

  // Samples vertices uniformly and induces edges.
  def nodeSampling(sc:SparkContext, graph:Graph[Int,Int], fraction:Double) = {
    val vertices = graph.vertices.sample(false, fraction, Random.nextLong)
    val vertexMap = vertices.collectAsMap()
    val edges = graph.edges.filter(e =>
      vertexMap.contains(e.srcId) && vertexMap.contains(e.dstId)
    )
    val graph2 = Graph(vertices, edges)
    graph2
  }

  // Get all edges with source vertexId of target
  def accumulateEdges(
    e:Array[Edge[Int]],
    target:VertexId) : ListBuffer[Edge[Int]] = 
  {
    val idx = binarySearchE(e, target)(0, e.size-1)
    var outEdges: ListBuffer[Edge[Int]] = ListBuffer()
    if (idx == -1)
      return outEdges
    outEdges.append(e(idx))
    var tIdx = idx+1
    var edge:Edge[Int] = null
    // get upper edges
    while (tIdx < e.size) {
      edge = e(tIdx)
      if (edge.srcId == target) {
        outEdges.append(edge)
        tIdx += 1
      } else {
        tIdx = e.size
      }
    }
    // get lower edges
    tIdx = idx-1
    while (tIdx > -1){
      edge = e(tIdx)
      if (edge.srcId == target) {
        outEdges.append(edge)
        tIdx -= 1
      } else {
        tIdx = -1
      }
    }
    outEdges
  }


  // Binary search to find an edge with target vertexId
  def binarySearchE(list: Array[Edge[Int]], target: VertexId)
    (start: Int=0, end: Int=list.length-1): Int = 
  {
    if (start>end) 
      return -1
    val mid = start + (end-start+1)/2
    if (list(mid).srcId == target)
      return mid
    else if (list(mid).srcId > target)
      return binarySearchE(list, target)(start, mid-1)
    else
      return binarySearchE(list, target)(mid+1, end)
  }
}
