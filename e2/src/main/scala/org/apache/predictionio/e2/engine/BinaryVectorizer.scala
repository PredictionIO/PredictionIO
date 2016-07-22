/** Copyright 2015 TappingStone, Inc.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package org.apache.predictionio.e2.engine

import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext._
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.linalg.Vector
import scala.collection.immutable.HashMap
import scala.collection.immutable.HashSet

class BinaryVectorizer(propertyMap : HashMap[(String, String), Int])
extends Serializable {

  val properties: Array[(String, String)] = propertyMap.toArray.sortBy(_._2).map(_._1)
  val numFeatures = propertyMap.size

  override def toString: String = {
    s"BinaryVectorizer($numFeatures): " + properties.map(e => s"(${e._1}, ${e._2})").mkString(",")
  }

  def toBinary(map :  Array[(String, String)]) : Vector = {
    val mapArr : Seq[(Int, Double)] = map.flatMap(
      e => propertyMap.get(e).map(idx => (idx, 1.0))
    )

    Vectors.sparse(numFeatures, mapArr)
  }
}


object BinaryVectorizer {
  def apply (input : RDD[HashMap[String, String]], properties : HashSet[String])
  : BinaryVectorizer = {
    new BinaryVectorizer(HashMap(
      input.flatMap(identity)
        .filter(e => properties.contains(e._1))
        .distinct
        .collect
        .zipWithIndex : _*
    ))
  }

  def apply(input: Seq[(String, String)]): BinaryVectorizer = {
    val indexed: Seq[((String, String), Int)] = input.zipWithIndex
    new BinaryVectorizer(HashMap(indexed:_*))
  }
}

