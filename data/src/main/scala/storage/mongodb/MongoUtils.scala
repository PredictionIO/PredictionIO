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

package io.prediction.data.storage.mongodb

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.conversions.scala._

object MongoUtils {
  /** Empty MongoDBObject. */
  val emptyObj = MongoDBObject()

  /** Modified from Salat. */
  def dbObjToMap(dbObj: DBObject): Map[String, Any] = {
    val builder = Map.newBuilder[String, Any]

    dbObj foreach {
      case (field, value) =>
        builder += field -> value
    }

    builder.result()
  }

  def dbObjToMapOfString(dbObj: DBObject): Map[String, String] = dbObjToMap(dbObj) mapValues { _.asInstanceOf[String] }
  def dbObjToMapOfDouble(dbObj: DBObject): Map[String, Double] =
    dbObjToMap(dbObj) mapValues { _.asInstanceOf[Double] }

  def basicDBListToListOfTuple2[T1, T2](dbList: BasicDBList): Seq[(T1, T2)] =
    dbList.map(_.asInstanceOf[BasicDBList]).map(l => {
      (l(0).asInstanceOf[T1], l(1).asInstanceOf[T2])
    })

  def basicDBListToListOfTuple8[T1, T2, T3, T4, T5, T6, T7, T8](dbList: BasicDBList): Seq[(T1, T2, T3, T4, T5, T6, T7, T8)] =
    dbList.map(_.asInstanceOf[BasicDBList]).map(l => {
      (
        l(0).asInstanceOf[T1],
        l(1).asInstanceOf[T2],
        l(2).asInstanceOf[T3],
        l(3).asInstanceOf[T4],
        l(4).asInstanceOf[T5],
        l(5).asInstanceOf[T6],
        l(6).asInstanceOf[T7],
        l(7).asInstanceOf[T8])
    })

  /** Converts MongoDBList to List of String. */
  def mongoDbListToListOfString(dbList: MongoDBList): List[String] = {
    dbList.toList.map(_.asInstanceOf[String])
  }

  /** Converts BasicDBList to List of String. */
  def basicDBListToListOfString(dbList: BasicDBList): List[String] = {
    dbList.toList.map(_.asInstanceOf[String])
  }

  /** Converts MongoDBList to List of Double. */
  def mongoDbListToListOfDouble(dbList: MongoDBList): List[Double] = {
    dbList.toList.map(_.asInstanceOf[Double])
  }

  /** Converts MongoDBList to List of Boolean. */
  def mongoDbListToListOfBoolean(dbList: MongoDBList): List[Boolean] = {
    dbList.toList.map(_.asInstanceOf[Boolean])
  }

  def mongoDbListToListofListOfString(dbList: MongoDBList): List[List[String]] = {
    dbList.toList.map(x => basicDBListToListOfString(x.asInstanceOf[BasicDBList]))
  }

  /**
   * Convert custom attributes Map into MongoDBObject and add prefix "ca_"
   *  eg.
   *  from attributes = Map("gender" -> "female", "member" -> "gold")
   *  to MongoDBObject("ca_gender" : "female", "ca_member" : "gold" )
   */
  def attributesToMongoDBObject(attributes: Map[String, Any]) = {
    MongoDBObject((attributes map { case (k, v) => ("ca_" + k, v) }).toList)
  }

  /** Extract custom attributes (with prefix "ca_") from DBObject, strip prefix and create Map[String, Any] */
  def getAttributesFromDBObject(dbObj: DBObject): Map[String, Any] = {
    dbObj.filter { case (k, v) => (k.startsWith("ca_")) }.toList.map { case (k, v) => (k.stripPrefix("ca_"), v) }.toMap
  }

  /**
   * Appends App ID to any ID.
   * Used for distinguishing different app's data within a single collection.
   */
  def idWithAppid(appid: Int, id: String) = appid + "_" + id
}
