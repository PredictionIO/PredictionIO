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
  idWithAppid
}

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.conversions.scala._
import com.github.nscala_time.time.Imports._

import io.prediction.data.storage.{ ItemSet, ItemSets }

class MongoItemSets(client: MongoClient, dbName: String) extends ItemSets {
  private val db = client(dbName)
  private val itemSetColl = db("itemSets")

  RegisterJodaTimeConversionHelpers()

  private def dbObjToItemSet(dbObj: DBObject) = {
    val appid = dbObj.as[Int]("appid")
    ItemSet(
      id = dbObj.as[String]("_id").drop(appid.toString.length + 1),
      appid = appid,
      iids = mongoDbListToListOfString(dbObj.as[MongoDBList]("iids"))
        .map(_.drop(appid.toString.length + 1)),
      t = dbObj.getAs[DateTime]("t")
    )
  }

  private class MongoItemSetsIterator(it: MongoCursor)
      extends Iterator[ItemSet] {
    def next = dbObjToItemSet(it.next)
    def hasNext = it.hasNext
  }

  /** Insert new ItemSet */
  def insert(itemSet: ItemSet): Unit = {
    val req = MongoDBObject(
      "_id" -> idWithAppid(itemSet.appid, itemSet.id),
      "appid" -> itemSet.appid,
      "iids" -> itemSet.iids.map(i => idWithAppid(itemSet.appid, i)))
    val opt = itemSet.t.map(t => MongoDBObject("t" -> t)).getOrElse(emptyObj)
    itemSetColl.save(req ++ opt)
  }

  /** Get an item set */
  def get(appid: Int, id: String): Option[ItemSet] = {
    itemSetColl.findOne(MongoDBObject("_id" -> idWithAppid(appid, id)))
      .map(dbObjToItemSet(_))
  }

  /** Get by appid */
  def getByAppid(appid: Int): Iterator[ItemSet] = {
    new MongoItemSetsIterator(itemSetColl.find(MongoDBObject("appid" -> appid)))
  }

  /** Get by appid and t >= startTime and t < untilTime */
  def getByAppidAndTime(appid: Int, startTime: DateTime, untilTime: DateTime):
    Iterator[ItemSet] = {
      new MongoItemSetsIterator(itemSetColl.find(
        MongoDBObject("appid" -> appid,
          "t" -> MongoDBObject("$gte" -> startTime, "$lt" -> untilTime))))
    }

  /** Delete itemSet */
  def delete(itemSet: ItemSet): Unit = {
    itemSetColl.remove(MongoDBObject("_id" -> idWithAppid(itemSet.appid,
      itemSet.id)))
  }

  /** Delete by appid */
  def deleteByAppid(appid: Int): Unit = {
    itemSetColl.remove(MongoDBObject("appid" -> appid))
  }

}
