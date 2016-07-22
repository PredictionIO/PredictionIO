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

import org.specs2.mutable._

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD

class BiMapSpec extends Specification {

  System.clearProperty("spark.driver.port")
  System.clearProperty("spark.hostPort")
  val sc = new SparkContext("local[4]", "BiMapSpec test")

  "BiMap created with map" should {

    val keys = Seq(1, 4, 6)
    val orgValues = Seq(2, 5, 7)
    val org = keys.zip(orgValues).toMap
    val bi = BiMap(org)

    "return correct values for each key of original map" in {
      val biValues = keys.map(k => bi(k))

      biValues must beEqualTo(orgValues)
    }

    "get return Option[V]" in {
      val checkKeys = keys ++ Seq(12345)
      val biValues = checkKeys.map(k => bi.get(k))
      val expected = orgValues.map(Some(_)) ++ Seq(None)

      biValues must beEqualTo(expected)
    }

    "getOrElse return value for each key of original map" in {
      val biValues = keys.map(k => bi.getOrElse(k, -1))

      biValues must beEqualTo(orgValues)
    }

    "getOrElse return default values for invalid key" in {
      val keys = Seq(999, -1, -2)
      val defaults = Seq(1234, 5678, 987)
      val biValues = keys.zip(defaults).map{ case (k,d) => bi.getOrElse(k, d) }

      biValues must beEqualTo(defaults)
    }

    "contains() returns true/false correctly" in {
      val checkKeys = keys ++ Seq(12345)
      val biValues = checkKeys.map(k => bi.contains(k))
      val expected = orgValues.map(_ => true) ++ Seq(false)

      biValues must beEqualTo(expected)
    }

    "same size as original map" in {
      (bi.size) must beEqualTo(org.size)
    }

    "take(2) returns BiMap of size 2" in {
      bi.take(2).size must beEqualTo(2)
    }

    "toMap contain same element as original map" in {
      (bi.toMap) must beEqualTo(org)
    }

    "toSeq contain same element as original map" in {
      (bi.toSeq) must containTheSameElementsAs(org.toSeq)
    }

    "inverse and return correct keys for each values of original map" in {
      val biKeys = orgValues.map(v => bi.inverse(v))
      biKeys must beEqualTo(keys)
    }

    "inverse with same size" in {
      bi.inverse.size must beEqualTo(org.size)
    }

    "inverse's inverse reference back to the same original object" in {
      // NOTE: reference equality
      bi.inverse.inverse == bi
    }
  }

  "BiMap created with duplicated values in map" should {
    val dup = Map(1 -> 2, 4 -> 7, 6 -> 7)
    "return IllegalArgumentException" in {
      BiMap(dup) must throwA[IllegalArgumentException]
    }
  }

  "BiMap.stringLong and stringInt" should {

    "create BiMap from set of string" in {
      val keys = Set("a", "b", "foo", "bar")
      val values: Seq[Long] = Seq(0, 1, 2, 3)

      val bi = BiMap.stringLong(keys)
      val biValues = keys.map(k => bi(k))

      val biInt = BiMap.stringInt(keys)
      val valuesInt: Seq[Int] = values.map(_.toInt)
      val biIntValues = keys.map(k => biInt(k))

      biValues must containTheSameElementsAs(values) and
        (biIntValues must containTheSameElementsAs(valuesInt))
    }

    "create BiMap from Array of unique string" in {
      val keys = Array("a", "b", "foo", "bar")
      val values: Seq[Long] = Seq(0, 1, 2, 3)

      val bi = BiMap.stringLong(keys)
      val biValues = keys.toSeq.map(k => bi(k))

      val biInt = BiMap.stringInt(keys)
      val valuesInt: Seq[Int] = values.map(_.toInt)
      val biIntValues = keys.toSeq.map(k => biInt(k))

      biValues must containTheSameElementsAs(values) and
        (biIntValues must containTheSameElementsAs(valuesInt))
    }

    "not guarantee sequential index for Array with duplicated string" in {
      val keys = Array("a", "b", "foo", "bar", "a", "b", "x")
      val dupValues: Seq[Long] = Seq(0, 1, 2, 3, 4, 5, 6)
      val values = keys.zip(dupValues).toMap.values.toSeq

      val bi = BiMap.stringLong(keys)
      val biValues = keys.toSet[String].map(k => bi(k))

      val biInt = BiMap.stringInt(keys)
      val valuesInt: Seq[Int] = values.map(_.toInt)
      val biIntValues = keys.toSet[String].map(k => biInt(k))

      biValues must containTheSameElementsAs(values) and
        (biIntValues must containTheSameElementsAs(valuesInt))
    }

    "create BiMap from RDD[String]" in {

      val keys = Seq("a", "b", "foo", "bar")
      val values: Seq[Long] = Seq(0, 1, 2, 3)
      val rdd = sc.parallelize(keys)

      val bi = BiMap.stringLong(rdd)
      val biValues = keys.map(k => bi(k))

      val biInt = BiMap.stringInt(rdd)
      val valuesInt: Seq[Int] = values.map(_.toInt)
      val biIntValues = keys.map(k => biInt(k))

      biValues must containTheSameElementsAs(values) and
        (biIntValues must containTheSameElementsAs(valuesInt))
    }

    "create BiMap from RDD[String] with duplicated string" in {

      val keys = Seq("a", "b", "foo", "bar", "a", "b", "x")
      val values: Seq[Long] = Seq(0, 1, 2, 3, 4)
      val rdd = sc.parallelize(keys)

      val bi = BiMap.stringLong(rdd)
      val biValues = keys.distinct.map(k => bi(k))

      val biInt = BiMap.stringInt(rdd)
      val valuesInt: Seq[Int] = values.map(_.toInt)
      val biIntValues = keys.distinct.map(k => biInt(k))

      biValues must containTheSameElementsAs(values) and
        (biIntValues must containTheSameElementsAs(valuesInt))
    }
  }

  step(sc.stop())
}
