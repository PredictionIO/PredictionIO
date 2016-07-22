package org.apache.predictionio.examples.pfriendrecommendation

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkContext._
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD

import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map


object DeltaSimRankRDD {
  var decay:Double = 0.8
  var numNodes:Int = 0

  def calculateNthIter(
    numNodes:Int,
    g:Graph[Int, Int],
    prevDelta:RDD[((VertexId,VertexId),Double)],
    outDegreeMap:scala.collection.Map[VertexId,Long])
    : RDD[((VertexId,VertexId), Double)] =
  {
    // No changes in last iteration -> no changes this iteration.
    if (prevDelta.count() == 0)
      return prevDelta

    val pairList = prevDelta.toArray
    val kvPairs = pairList.map(pair => {
      val a = pair._1._1.toInt
      val b = pair._1._2.toInt
      val delta = pair._2
      val b_adj = g.edges.filter(e => e.dstId == b).map(x=>x.srcId)
      val a_adj = g.edges.filter(e => e.dstId == a).map(x=>x.srcId)

      val scorePairs = a_adj.cartesian(b_adj)
      scorePairs.filter(pair=> pair._1 != pair._2).map(pair => (pair, delta))
    })

    var union = kvPairs(0)
    var index = 0
    for (index <- 1 to kvPairs.size-1)
      union = union ++ kvPairs(index)

    val newDelta = union.reduceByKey(_ + _)
      .map(k =>
        (k._1, k._2*decay/(outDegreeMap(k._1._1) * outDegreeMap(k._1._2)))
      )
    newDelta
  }

  def identityMatrix(sc:SparkContext, numCols:Long) : RDD[(Long, Double)] =
  {
    val numElements = numCols * numCols
    val arr = Array[Long]((0L to numElements).toList:_*)
    // (Score, Index), where (x,y) = (Index/numCols, Index%numCols)
    val pairs = arr.map(x => {
      if (x/numCols == x % numCols)
        (x, 1.0)
      else
        (x, 0.0)
    })
    sc.parallelize(pairs)
  }

  def matrixToIndices(x:Int, y:Int, numCols:Int) = {
    x + y * numCols
  }

  def joinDelta(
    prevIter:RDD[(Long, Double)],
    numCols:Int,
    delta:RDD[((VertexId,VertexId), Double)]) : RDD[(Long,Double)] =
  {
    val deltaToIndex:RDD[(Long,Double)] = delta.map(x => {
      val index = (x._1._1-1)*numCols + (x._1._2-1)
      (index, x._2)
    })
    println("detaToIndex")
    deltaToIndex.foreach(println(_))
    val newIter = prevIter.leftOuterJoin(deltaToIndex)
    val newScores = newIter.map(x => {
      val index = x._1
      if (x._2._2.isDefined) {
        (index, x._2._1 + x._2._2.get)
      } else {
        (index, x._2._1)
      }
    })
    newScores
  }

  def getOutdegreeMap(g:Graph[Int,Int]) : scala.collection.Map[VertexId, Long] =
  {
    g.edges.map(edge => (edge.srcId,1L))
      .reduceByKey(_ + _)
      .collectAsMap()
  }

  def compute(
    g:Graph[Int,Int],
    numIterations:Int,
    identityMatrix:RDD[(VertexId,Double)],
    newDecay:Double) : RDD[(VertexId,Double)] =
  {
    numNodes = g.vertices.count().toInt
    decay = newDecay

    // Build the identity matrix representing 0-th iteration of SimRank
    val s0 = identityMatrix
    val outDegreeMap:scala.collection.Map[VertexId,Long] = getOutdegreeMap(g)
    val s0Delta = g.vertices.map(vertex => ((vertex._1, vertex._1), 1.0))

    var prevSimrank = s0
    var prevDelta = s0Delta

    println(s"initial")
    s0.foreach(println(_))
    prevDelta.foreach(println(_))

    for (i <- 0 to numIterations) {
      val nextIterDelta = calculateNthIter(numNodes, g, prevDelta, outDegreeMap)
      val nextIterSimrank = joinDelta(prevSimrank, numNodes, nextIterDelta)
      println(s"iteration: ${i}")
      nextIterDelta.foreach(println(_))
      nextIterSimrank.foreach(println(_))
      prevSimrank = nextIterSimrank
      prevDelta = nextIterDelta
    }

    prevSimrank
  }

  // Make all vertexId in one contiguous number range
  def normalizeGraph(g:Graph[Int,Int]) = {
    var counter = 0.toLong
    val hash = Map[VertexId, Long]()

    val v = g.vertices.map( pair => {
      hash(pair._1) = counter
      counter += 1
      (counter - 1, pair._2)
    })

    val e = g.edges.map( (e:Edge[Int]) => {
      if (hash.contains(e.srcId)) {
        e.srcId = hash(e.srcId)
      } else {
        hash += (e.srcId -> counter)
        counter += 1
        e.srcId = counter - 1
      }

      if (hash.contains(e.dstId)) {
        e.dstId = hash(e.dstId)
      } else {
        hash += (e.dstId -> counter)
        counter += 1
        e.dstId = counter - 1
      }
      e
    })

    val g2 = Graph(v,e)
    g2
  }

}
