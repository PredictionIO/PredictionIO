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

import org.apache.predictionio.annotation.Experimental

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

/**
 * :: Experimental ::
 */
@Experimental
class EntityIdIxMap(val idToIx: BiMap[String, Long]) extends Serializable {

  val ixToId: BiMap[Long, String] = idToIx.inverse

  def apply(id: String): Long = idToIx(id)

  def apply(ix: Long): String = ixToId(ix)

  def contains(id: String): Boolean = idToIx.contains(id)

  def contains(ix: Long): Boolean = ixToId.contains(ix)

  def get(id: String): Option[Long] = idToIx.get(id)

  def get(ix: Long): Option[String] = ixToId.get(ix)

  def getOrElse(id: String, default: => Long): Long =
    idToIx.getOrElse(id, default)

  def getOrElse(ix: Long, default: => String): String =
    ixToId.getOrElse(ix, default)

  def toMap: Map[String, Long] = idToIx.toMap

  def size: Long = idToIx.size

  def take(n: Int): EntityIdIxMap = new EntityIdIxMap(idToIx.take(n))

  override def toString: String = idToIx.toString
}

/** :: Experimental :: */
@Experimental
object EntityIdIxMap {
  def apply(keys: RDD[String]): EntityIdIxMap = {
    new EntityIdIxMap(BiMap.stringLong(keys))
  }
}

/** :: Experimental :: */
@Experimental
class EntityMap[A](val idToData: Map[String, A],
  override val idToIx: BiMap[String, Long]) extends EntityIdIxMap(idToIx) {

  def this(idToData: Map[String, A]) = this(
    idToData,
    BiMap.stringLong(idToData.keySet)
  )

  def data(id: String): A = idToData(id)

  def data(ix: Long): A = idToData(ixToId(ix))

  def getData(id: String): Option[A] = idToData.get(id)

  def getData(ix: Long): Option[A] = idToData.get(ixToId(ix))

  def getOrElseData(id: String, default: => A): A =
    getData(id).getOrElse(default)

  def getOrElseData(ix: Long, default: => A): A =
    getData(ix).getOrElse(default)

  override def take(n: Int): EntityMap[A] = {
    val newIdToIx = idToIx.take(n)
    new EntityMap[A](idToData.filterKeys(newIdToIx.contains(_)), newIdToIx)
  }

  override def toString: String = {
    s"idToData: ${idToData.toString} " + s"idToix: ${idToIx.toString}"
  }
}
