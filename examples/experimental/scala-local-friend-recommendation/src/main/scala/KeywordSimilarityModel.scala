package org.apache.predictionio.examples.friendrecommendation

import scala.collection.immutable.HashMap

class KeywordSimilarityModel (
  // External-internal id map
  val userIdMap: HashMap[Int, Int],
  val itemIdMap: HashMap[Int, Int],
  // Keyword array, internal id index, term-weight map item
  val userKeyword: Array[HashMap[Int, Double]],
  val itemKeyword: Array[HashMap[Int, Double]],
  // Weight and threshold trained
  val keywordSimWeight: Double,
  val keywordSimThreshold: Double
) extends Serializable
