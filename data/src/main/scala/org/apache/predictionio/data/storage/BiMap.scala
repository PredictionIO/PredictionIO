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

package org.apache.predictionio.data.storage

import scala.collection.immutable.HashMap

import org.apache.spark.rdd.RDD

/** Immutable Bi-directional Map
  *
  */
class BiMap[K, V] private[predictionio] (
  private val m: Map[K, V],
  private val i: Option[BiMap[V, K]] = None
  ) extends Serializable {

  // NOTE: make inverse's inverse point back to current BiMap
  val inverse: BiMap[V, K] = i.getOrElse {
    val rev = m.map(_.swap)
    require((rev.size == m.size),
      s"Failed to create reversed map. Cannot have duplicated values.")
    new BiMap(rev, Some(this))
  }

  def get(k: K): Option[V] = m.get(k)

  def getOrElse(k: K, default: => V): V = m.getOrElse(k, default)

  def contains(k: K): Boolean = m.contains(k)

  def apply(k: K): V = m.apply(k)

  /** Converts to a map.
    * @return a map of type immutable.Map[K, V]
    */
  def toMap: Map[K, V] = m

  /** Converts to a sequence.
    * @return a sequence containing all elements of this map
    */
  def toSeq: Seq[(K, V)] = m.toSeq

  def size: Int = m.size

  def take(n: Int): BiMap[K, V] = BiMap(m.take(n))

  override def toString: String = m.toString
}

object BiMap {

  def apply[K, V](x: Map[K, V]): BiMap[K, V] = new BiMap(x)

  /** Create a BiMap[String, Long] from a set of String. The Long index starts
    * from 0.
    * @param keys a set of String
    * @return a String to Long BiMap
    */
  def stringLong(keys: Set[String]): BiMap[String, Long] = {
    val hm = HashMap(keys.toSeq.zipWithIndex.map(t => (t._1, t._2.toLong)) : _*)
    new BiMap(hm)
  }

  /** Create a BiMap[String, Long] from an array of String.
    * NOTE: the the array cannot have duplicated element.
    * The Long index starts from 0.
    * @param keys a set of String
    * @return a String to Long BiMap
    */
  def stringLong(keys: Array[String]): BiMap[String, Long] = {
    val hm = HashMap(keys.zipWithIndex.map(t => (t._1, t._2.toLong)) : _*)
    new BiMap(hm)
  }

  /** Create a BiMap[String, Long] from RDD[String]. The Long index starts
    * from 0.
    * @param keys RDD of String
    * @return a String to Long BiMap
    */
  def stringLong(keys: RDD[String]): BiMap[String, Long] = {
    stringLong(keys.distinct.collect)
  }

  /** Create a BiMap[String, Int] from a set of String. The Int index starts
    * from 0.
    * @param keys a set of String
    * @return a String to Int BiMap
    */
  def stringInt(keys: Set[String]): BiMap[String, Int] = {
    val hm = HashMap(keys.toSeq.zipWithIndex : _*)
    new BiMap(hm)
  }

  /** Create a BiMap[String, Int] from an array of String.
    * NOTE: the the array cannot have duplicated element.
    * The Int index starts from 0.
    * @param keys a set of String
    * @return a String to Int BiMap
    */
  def stringInt(keys: Array[String]): BiMap[String, Int] = {
    val hm = HashMap(keys.zipWithIndex : _*)
    new BiMap(hm)
  }

  /** Create a BiMap[String, Int] from RDD[String]. The Int index starts
    * from 0.
    * @param keys RDD of String
    * @return a String to Int BiMap
    */
  def stringInt(keys: RDD[String]): BiMap[String, Int] = {
    stringInt(keys.distinct.collect)
  }

  private[this] def stringDoubleImpl(keys: Seq[String])
  : BiMap[String, Double] = {
    val ki = keys.zipWithIndex.map(e => (e._1, e._2.toDouble))
    new BiMap(HashMap(ki : _*))
  }

  /** Create a BiMap[String, Double] from a set of String. The Double index
    * starts from 0.
    * @param keys a set of String
    * @return a String to Double BiMap
    */
  def stringDouble(keys: Set[String]): BiMap[String, Double] = {
    // val hm = HashMap(keys.toSeq.zipWithIndex.map(_.toDouble) : _*)
    // new BiMap(hm)
    stringDoubleImpl(keys.toSeq)
  }

  /** Create a BiMap[String, Double] from an array of String.
    * NOTE: the the array cannot have duplicated element.
    * The Double index starts from 0.
    * @param keys a set of String
    * @return a String to Double BiMap
    */
  def stringDouble(keys: Array[String]): BiMap[String, Double] = {
    // val hm = HashMap(keys.zipWithIndex.mapValues(_.toDouble) : _*)
    // new BiMap(hm)
    stringDoubleImpl(keys.toSeq)
  }

  /** Create a BiMap[String, Double] from RDD[String]. The Double index starts
    * from 0.
    * @param keys RDD of String
    * @return a String to Double BiMap
    */
  def stringDouble(keys: RDD[String]): BiMap[String, Double] = {
    stringDoubleImpl(keys.distinct.collect)
  }
}
