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

import io.prediction.data.storage.mongodb.MongoUtils.{
  emptyObj,
  mongoDbListToListOfString,
  idWithAppid,
  mongoDbListToListOfDouble,
  mongoDbListToListOfBoolean,
  attributesToMongoDBObject,
  getAttributesFromDBObject,
  basicDBListToListOfTuple2,
  basicDBListToListOfTuple8
}

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.conversions.scala._
import com.github.nscala_time.time.Imports._

import io.prediction.data.storage.{ ItemTrend, ItemTrends }

/** MongoDB implementation of ItemTrends. */
class MongoItemTrends(client: MongoClient, dbName: String) extends ItemTrends {
  private val db = client(dbName)
  private val itemColl = db("itemTrends")

  RegisterJodaTimeConversionHelpers()

  def insert(itemTrend: ItemTrend) = {
    val itemTrendObj = getIdDbObj(itemTrend.appid, itemTrend.id) ++
      MongoDBObject(
        "appid" -> itemTrend.appid,
        "ct" -> itemTrend.ct,
        //"priceRaw" -> itemTrend.priceRaw,
        "daily" -> itemTrend.daily
      )
    itemColl.save(itemTrendObj)
  }

  def contains(appid: Int, id: String): Boolean =
    !itemColl.findOne(getIdDbObj(appid, id), MongoDBObject("_id" -> 1)).isEmpty

  def get(appid: Int, id: String) =
    itemColl.findOne(getIdDbObj(appid, id)) map { dbObjToItem(_) }

  def getByAppid(appid: Int) = {
    new MongoItemTrendsIterator(itemColl.find(MongoDBObject("appid" -> appid)))
  }

  def getByIds(appid: Int, ids: Seq[String]) = {
    itemColl.find(MongoDBObject("_id" -> MongoDBObject("$in" -> ids.map(idWithAppid(appid, _))))).toList map { dbObjToItem(_) }
  }

  //def update(itemTrend: ItemTrend) = {
  /*
    val idObj = getIdDbObj(itemTrend.appid, itemTrend.id)

    val itemTrendObj = MongoDBObject(
      "appid" -> itemTrend.appid,
      "ct" -> itemTrend.ct,
      "priceRaw" -> itemTrend.priceRaw
    )
    */
  //itemColl.update(idObj, idObj ++ itemTrendObj)
  //}

  /*
  def appendPrice(appid: Int, id: String, t: DateTime, p: Double) =
    extendPrice(appid, id, List((t, p)))

  def extendPrice(appid: Int, id: String, newPrice: Seq[(DateTime, Double)]) =
    {
      val idObj = getIdDbObj(appid, id)
      // Should raise failure if object doesn't exist
      itemColl.update(
        idObj,
        MongoDBObject("$push" -> Map("priceRaw" -> Map("$each" -> newPrice))))
    }
    */

  def delete(appid: Int, id: String) = itemColl.remove(getIdDbObj(appid, id))

  def delete(itemTrend: ItemTrend) = delete(itemTrend.appid, itemTrend.id)

  def deleteByAppid(appid: Int): Unit = {
    itemColl.remove(MongoDBObject("appid" -> appid))
  }

  def countByAppid(appid: Int): Long = itemColl.count(MongoDBObject("appid" -> appid))

  private def getIdDbObj(appid: Int, id: String) =
    MongoDBObject("_id" -> idWithAppid(appid, id))

  private def dbObjToItem(dbObj: DBObject) = {
    val appid = dbObj.as[Int]("appid")
    ItemTrend(
      id = dbObj.as[String]("_id").drop(appid.toString.length + 1),
      appid = appid,
      ct = dbObj.as[DateTime]("ct"),
      // This is a workaround to a problem where the underlying value in mongo
      // can be an integer. We extract the value as a Number, then take the
      // double.
      //priceRaw = basicDBListToListOfTuple2[DateTime, Number](
      //  dbObj.as[MongoDBList]("priceRaw")).map(e => (e._1, e._2.doubleValue)),
      daily = basicDBListToListOfTuple8[DateTime, Number, Number, Number, Number, Number, Number, Boolean](
        dbObj.as[MongoDBList]("daily")).map(e =>
          (e._1, e._2.doubleValue, e._3.doubleValue, e._4.doubleValue,
            e._5.doubleValue, e._6.doubleValue, e._7.doubleValue, e._8))
    )
  }

  class MongoItemTrendsIterator(it: MongoCursor) extends Iterator[ItemTrend] {
    def next = dbObjToItem(it.next)
    def hasNext = it.hasNext
  }
}
