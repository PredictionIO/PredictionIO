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
  attributesToMongoDBObject,
  getAttributesFromDBObject
}

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.conversions.scala._
import com.github.nscala_time.time.Imports._

import io.prediction.data.storage.{ U2IAction, U2IActions }

/** MongoDB implementation of Items. */
class MongoU2IActions(client: MongoClient, dbName: String) extends U2IActions {
  private val db = client(dbName)
  private val emptyObj = MongoDBObject()
  private val u2iActionColl = db("u2iActions")

  RegisterJodaTimeConversionHelpers()

  def insert(u2iAction: U2IAction) = {
    val appid = MongoDBObject("appid" -> u2iAction.appid)
    val action = MongoDBObject("action" -> u2iAction.action)
    val uid = MongoDBObject("uid" -> idWithAppid(u2iAction.appid, u2iAction.uid))
    val iid = MongoDBObject("iid" -> idWithAppid(u2iAction.appid, u2iAction.iid))
    val t = MongoDBObject("t" -> u2iAction.t)
    val lnglat = u2iAction.latlng map { l => MongoDBObject("lnglat" -> MongoDBList(l._2, l._1)) } getOrElse emptyObj
    val v = u2iAction.v map { v => MongoDBObject("v" -> v) } getOrElse emptyObj
    val price = u2iAction.price map { p => MongoDBObject("price" -> p) } getOrElse emptyObj
    u2iActionColl.insert(appid ++ action ++ uid ++ iid ++ t ++ lnglat ++ v ++ price)
  }

  def getAllByAppid(appid: Int) = new MongoU2IActionIterator(u2iActionColl.find(MongoDBObject("appid" -> appid)))

  def getByAppidAndTime(appid: Int, startTime: DateTime, untilTime: DateTime):
    Iterator[U2IAction] = {
      new MongoU2IActionIterator(u2iActionColl.find(
        MongoDBObject("appid" -> appid,
          "t" -> MongoDBObject("$gte" -> startTime, "$lt" -> untilTime))
      ))
    }

  def getAllByAppidAndUidAndIids(appid: Int, uid: String, iids: Seq[String]) = new MongoU2IActionIterator(
    u2iActionColl.find(MongoDBObject("appid" -> appid, "uid" -> idWithAppid(appid, uid), "iid" -> MongoDBObject("$in" -> iids.map(idWithAppid(appid, _)))))
  )

  def getAllByAppidAndIid(appid: Int, iid: String, sortedByUid: Boolean = true): Iterator[U2IAction] = {
    if (sortedByUid)
      new MongoU2IActionIterator(u2iActionColl.find(MongoDBObject("appid" -> appid, "iid" -> idWithAppid(appid, iid))).sort(MongoDBObject("uid" -> 1)))
    else
      new MongoU2IActionIterator(u2iActionColl.find(MongoDBObject("appid" -> appid, "iid" -> idWithAppid(appid, iid))))
  }

  def deleteByAppid(appid: Int): Unit = {
    u2iActionColl.remove(MongoDBObject("appid" -> appid))
  }

  def countByAppid(appid: Int): Long = u2iActionColl.count(MongoDBObject("appid" -> appid))

  private def dbObjToU2IAction(dbObj: DBObject) = {
    val appid = dbObj.as[Int]("appid")
    U2IAction(
      appid = appid,
      action = dbObj.as[String]("action"),
      uid = dbObj.as[String]("uid").drop(appid.toString.length + 1),
      iid = dbObj.as[String]("iid").drop(appid.toString.length + 1),
      t = dbObj.as[DateTime]("t"),
      latlng = dbObj.getAs[MongoDBList]("lnglat") map { lnglat => (lnglat(1).asInstanceOf[Double], lnglat(0).asInstanceOf[Double]) },
      v = dbObj.getAs[Int]("v"),
      price = dbObj.getAs[Double]("price")
    )
  }

  class MongoU2IActionIterator(it: MongoCursor) extends Iterator[U2IAction] {
    def next = dbObjToU2IAction(it.next)
    def hasNext = it.hasNext
  }
}
