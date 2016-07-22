package org.apache.predictionio.examples.friendrecommendation

import scala.collection.immutable.HashMap

class FriendRecommendationTrainingData (
  // Designed to fit random, keyword similarity methods and simrank now
  // Will be updated to to fit more advanced algorithms when they are developed
  // External-internal id map
  val userIdMap: HashMap[Int, Int],
  val itemIdMap: HashMap[Int, Int],
  // Keyword array, internal id index, term-weight map item
  val userKeyword: Array[HashMap[Int, Double]],
  val itemKeyword: Array[HashMap[Int, Double]],
  // User relationship array, 
  // src internal id index, dest-internal-id-weight list item
  val socialAction: Array[List[(Int, Int)]],
  // Training record for training purpose
  val trainingRecord: Stream[(Int, Int, Boolean)]
) extends Serializable
