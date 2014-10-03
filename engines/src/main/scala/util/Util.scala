/** Copyright 2014 TappingStone, Inc.
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

package io.prediction.engines.util

import io.prediction.controller.NiceRendering

import org.apache.mahout.cf.taste.model.DataModel
import org.apache.mahout.cf.taste.model.Preference
import org.apache.mahout.cf.taste.model.PreferenceArray
import org.apache.mahout.cf.taste.impl.model.GenericDataModel
import org.apache.mahout.cf.taste.impl.model.GenericBooleanPrefDataModel
import org.apache.mahout.cf.taste.impl.model.GenericPreference
import org.apache.mahout.cf.taste.impl.model.GenericUserPreferenceArray
import org.apache.mahout.cf.taste.impl.common.FastByIDMap
import org.apache.mahout.cf.taste.impl.common.FastIDSet

import scala.collection.JavaConversions._
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.asScalaSet
import java.util.{ List => JList }
import java.util.{ Set => JSet }
import java.lang.{ Integer => JInteger }
import java.lang.{ Float => JFloat }
import java.lang.{ Long => JLong }

import grizzled.slf4j.Logger
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.io.FileInputStream
import java.io.ObjectInputStream

import scala.io.Source
import java.io.PrintWriter
import java.io.File

/** Mahout Integration helper functions */
object MahoutUtil {

  val logger = Logger(MahoutUtil.getClass)

  /** Java version of buildDataModel */
  def jBuildDataModel(ratingSeq: JList[Tuple4[JInteger, JInteger, JFloat, JLong]]): DataModel = {
    buildDataModel(asScalaBuffer(ratingSeq).toList.asInstanceOf[List[(Int, Int, Float, Long)]])
  }

  def jBuildBooleanPrefDataModel(ratingSeq: JList[Tuple3[JInteger, JInteger, JLong]]): DataModel = {
    buildBooleanPrefDataModel(asScalaBuffer(ratingSeq).toList.asInstanceOf[List[(Int, Int, Long)]])
  }

  /** Build DataModel with Seq of (uid, iid, rating, timestamp)
   *  NOTE: assume no duplicated rating on same iid by the same user
   */
  def buildDataModel(
    ratingSeq: Seq[(Int, Int, Float, Long)]): DataModel = {

    val allPrefs = new FastByIDMap[PreferenceArray]()
    val allTimestamps = new FastByIDMap[FastByIDMap[java.lang.Long]]()

    ratingSeq.groupBy(_._1)
      .foreach { case (uid, ratingList) =>
        val userID = uid.toLong
        // preference of items for this user
        val userPrefs = new GenericUserPreferenceArray(ratingList.size)
        // timestamp of items for this user
        val userTimestamps = new FastByIDMap[java.lang.Long]()

        ratingList.zipWithIndex
          .foreach { case (r, i) =>
            val itemID = r._2.toLong
            val pref = new GenericPreference(userID, itemID, r._3)
            userPrefs.set(i, pref)
            userTimestamps.put(itemID, r._4)
        }

        allPrefs.put(userID, userPrefs)
        allTimestamps.put(userID, userTimestamps)
      }

    new GenericDataModel(allPrefs, allTimestamps)
  }

  /** Build DataModel with Seq of (uid, iid, timestamp)
   *  NOTE: assume no duplicated iid by the same user
   */
  def buildBooleanPrefDataModel(
    ratingSeq: Seq[(Int, Int, Long)]): DataModel = {

    val allPrefs = new FastByIDMap[FastIDSet]()
    val allTimestamps = new FastByIDMap[FastByIDMap[java.lang.Long]]()

    ratingSeq.foreach { case (uid, iid, t) =>
      val userID = uid.toLong
      val itemID = iid.toLong

      // item
      val idSet = allPrefs.get(userID)
      if (idSet == null) {
        val newIdSet = new FastIDSet()
        newIdSet.add(itemID)
        allPrefs.put(userID, newIdSet)
      } else {
        idSet.add(itemID)
      }
      // timestamp
      val timestamps = allTimestamps.get(userID)
      if (timestamps == null) {
        val newTimestamps = new FastByIDMap[java.lang.Long]
        newTimestamps.put(itemID, t)
        allTimestamps.put(userID, newTimestamps)
      } else {
        timestamps.put(itemID, t)
      }
    }

    new GenericBooleanPrefDataModel(allPrefs, allTimestamps)
  }

}


/** Math helper functions */
object MathUtil {

  /** Average precision at k */
  def averagePrecisionAtK[T](k: Int, p: Seq[T], r: Set[T]): Double = {
    // supposedly the predictedItems.size should match k
    // NOTE: what if predictedItems is less than k? use the avaiable items as k.
    val n = scala.math.min(p.size, k)

    // find if each element in the predictedItems is one of the relevant items
    // if so, map to 1. else map to 0
    // (0, 1, 0, 1, 1, 0, 0)
    val rBin: Seq[Int] = p.take(n).map { x => if (r(x)) 1 else 0 }
    val pAtKNom = rBin.scanLeft(0)(_ + _)
      .drop(1) // drop 1st one which is initial 0
      .zip(rBin)
      .map(t => if (t._2 != 0) t._1.toDouble else 0.0)
    // ( number of hits at this position if hit or 0 if miss )

    val pAtKDenom = 1 to rBin.size
    val pAtK = pAtKNom.zip(pAtKDenom).map { t => t._1 / t._2 }
    val apAtKDenom = scala.math.min(n, r.size)
    if (apAtKDenom == 0) 0 else pAtK.sum / apAtKDenom
  }

  /** Java's Average precision at k */
  def jAveragePrecisionAtK[T](k: Integer, p: JList[T], r: JSet[T]): Double = {
    averagePrecisionAtK(k, asScalaBuffer[T](p).toList, asScalaSet[T](r).toSet)
  }
}

object MetricsVisualization {
  class ObjectInputStreamWithCustomClassLoader(
    fileInputStream: FileInputStream
  ) extends ObjectInputStream(fileInputStream) {
    override def resolveClass(desc: java.io.ObjectStreamClass): Class[_] = {
      try { Class.forName(desc.getName, false, getClass.getClassLoader) }
      catch { case ex: ClassNotFoundException => super.resolveClass(desc) }
    }
  }

  def save[T](data: T, path: String) {
    println(s"Output to: $path")
    val oos = new ObjectOutputStream(new FileOutputStream(path))
    oos.writeObject(data)
    oos.close()
  }

  def load[T](path: String): T = {
    val ois = new ObjectInputStreamWithCustomClassLoader(new FileInputStream(path))
    val obj = ois.readObject().asInstanceOf[T]
    ois.close
    return obj
  }

  def render[T <: NiceRendering](data: NiceRendering, path: String) {
    val htmlPath = s"${path}.html"
    println(s"OutputPath: $htmlPath")
    val dataClass = data.getClass

    val htmlWriter = new PrintWriter(new File(htmlPath))
    val html = dataClass.getMethod("toHTML").invoke(data).asInstanceOf[String]
    htmlWriter.write(html)
    htmlWriter.close()

    val jsonPath = s"${path}.json"
    val jsonWriter = new PrintWriter(new File(jsonPath))
    val json = dataClass.getMethod("toJSON").invoke(data).asInstanceOf[String]
    jsonWriter.write(json)
    jsonWriter.close()
  }
}
