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

import io.prediction.data.storage.{ Item, Items }

/** MongoDB implementation of Items. */
class MongoItems(client: MongoClient, dbName: String) extends Items {
  private val db = client(dbName)
  private val itemColl = db("items")

  RegisterJodaTimeConversionHelpers()

  /** Indices and hints. */
  val starttimeIndex = MongoDBObject("starttime" -> -1)
  val lnglatIndex = MongoDBObject("lnglat" -> "2d")
  itemColl.ensureIndex(starttimeIndex)
  itemColl.ensureIndex(lnglatIndex)

  def insert(item: Item) = {
    val id = MongoDBObject("_id" -> idWithAppid(item.appid, item.id))
    val appid = MongoDBObject("appid" -> item.appid)
    val ct = MongoDBObject("ct" -> item.ct)
    val itypes = MongoDBObject("itypes" -> item.itypes)
    val starttime = item.starttime map { s => MongoDBObject("starttime" -> s) } getOrElse emptyObj
    val endtime = item.endtime map { e => MongoDBObject("endtime" -> e) } getOrElse emptyObj
    val price = item.price map { p => MongoDBObject("price" -> p) } getOrElse emptyObj
    val profit = item.profit map { p => MongoDBObject("profit" -> p) } getOrElse emptyObj
    val lnglat = item.latlng map { l => MongoDBObject("lnglat" -> MongoDBList(l._2, l._1)) } getOrElse emptyObj
    val inactive = item.inactive map { i => MongoDBObject("inactive" -> i) } getOrElse emptyObj
    //val attributes = item.attributes map { a => MongoDBObject("attributes" -> a) } getOrElse emptyObj
    // add "ca_" prefix for custom attributes
    val attributes = item.attributes map { a => attributesToMongoDBObject(a) } getOrElse emptyObj
    itemColl.save(id ++ appid ++ ct ++ itypes ++ starttime ++ endtime ++ price ++ profit ++ lnglat ++ inactive ++ attributes)
  }

  def get(appid: Int, id: String) = {
    itemColl.findOne(MongoDBObject("_id" -> idWithAppid(appid, id))) map { dbObjToItem(_) }
  }

  def getByAppid(appid: Int) = new MongoItemsIterator(itemColl.find(MongoDBObject("appid" -> appid)))

  def getByAppidAndLatlng(appid: Int, latlng: Tuple2[Double, Double], within: Option[Double], unit: Option[String]) = {
    val earthRadiusInKm = 6371
    val earthRadiusInMiles = 3959

    val nearSphereObj = MongoDBObject("$nearSphere" -> MongoDBList(latlng._2, latlng._1))
    val maxDistObj = within map { maxDist =>
      unit match {
        case Some("km") => MongoDBObject("$maxDistance" -> maxDist / earthRadiusInKm)
        case Some("mi") => MongoDBObject("$maxDistance" -> maxDist / earthRadiusInMiles)
        case _          => MongoDBObject("$maxDistance" -> maxDist / earthRadiusInKm)
      }
    } getOrElse emptyObj

    new MongoItemsIterator(itemColl.find(MongoDBObject("appid" -> appid, "lnglat" -> (nearSphereObj ++ maxDistObj))))
  }

  def getByAppidAndItypes(appid: Int, itypes: Seq[String]): Iterator[Item] = {
    getByAppidAndItypesAndTime(appid, optItypes = Some(itypes), optTime = None)
  }

  def getByAppidAndItypesAndTime(appid: Int,
    optItypes: Option[Seq[String]] = None,
    optTime: Option[DateTime] = None): Iterator[Item] = {
    val queryObj = ("appid" $eq appid)
    if (!optItypes.isEmpty)
      queryObj ++= ("itypes" $in optItypes.get)
    if (!optTime.isEmpty) {
      val time = optTime.get
      queryObj ++= ("starttime" $lt time)
      // Need to create an ugly DBObject as casbah don't have scala-ble support
      // for $or
      queryObj ++= DBObject("$or" -> MongoDBList(
        ("endtime" $exists false),
        ("endtime" $gt time)))
    }
    new MongoItemsIterator(itemColl.find(queryObj))
  }

  def getByIds(appid: Int, ids: Seq[String]) = {
    itemColl.find(MongoDBObject("_id" -> MongoDBObject("$in" -> ids.map(idWithAppid(appid, _))))).toList map { dbObjToItem(_) }
  }

  def getRecentByIds(appid: Int, ids: Seq[String]) = {
    itemColl.find(MongoDBObject("_id" -> MongoDBObject("$in" -> ids.map(idWithAppid(appid, _))))).sort(starttimeIndex).toList map { dbObjToItem(_) }
  }

  def update(item: Item) = {
    val id = MongoDBObject("_id" -> idWithAppid(item.appid, item.id))
    val appid = MongoDBObject("appid" -> item.appid)
    val ct = MongoDBObject("ct" -> item.ct)
    val itypes = MongoDBObject("itypes" -> item.itypes)
    val starttime = item.starttime map { s => MongoDBObject("starttime" -> s) } getOrElse emptyObj
    val endtime = item.endtime map { e => MongoDBObject("endtime" -> e) } getOrElse emptyObj
    val price = item.price map { p => MongoDBObject("price" -> p) } getOrElse emptyObj
    val profit = item.profit map { p => MongoDBObject("profit" -> p) } getOrElse emptyObj
    val lnglat = item.latlng map { l => MongoDBObject("lnglat" -> MongoDBList(l._2, l._1)) } getOrElse emptyObj
    val inactive = item.inactive map { i => MongoDBObject("inactive" -> i) } getOrElse emptyObj
    //val attributes = item.attributes map { a => MongoDBObject("attributes" -> a) } getOrElse emptyObj
    // add "ca_" prefix for custom attributes
    val attributes = item.attributes map { a => attributesToMongoDBObject(a) } getOrElse emptyObj
    itemColl.update(id, id ++ appid ++ ct ++ itypes ++ starttime ++ endtime ++ price ++ profit ++ lnglat ++ inactive ++ attributes)
  }

  def delete(appid: Int, id: String) = itemColl.remove(MongoDBObject("_id" -> idWithAppid(appid, id)))
  def delete(item: Item) = delete(item.appid, item.id)

  def deleteByAppid(appid: Int): Unit = {
    itemColl.remove(MongoDBObject("appid" -> appid))
  }

  def countByAppid(appid: Int): Long = itemColl.count(MongoDBObject("appid" -> appid))

  private def dbObjToItem(dbObj: DBObject) = {
    val appid = dbObj.as[Int]("appid")
    Item(
      id = dbObj.as[String]("_id").drop(appid.toString.length + 1),
      appid = appid,
      ct = dbObj.as[DateTime]("ct"),
      itypes = mongoDbListToListOfString(dbObj.as[MongoDBList]("itypes")),
      starttime = dbObj.getAs[DateTime]("starttime"),
      endtime = dbObj.getAs[DateTime]("endtime"),
      price = dbObj.getAs[Double]("price"),
      profit = dbObj.getAs[Double]("profit"),
      latlng = dbObj.getAs[MongoDBList]("lnglat") map { lnglat => (lnglat(1).asInstanceOf[Double], lnglat(0).asInstanceOf[Double]) },
      inactive = dbObj.getAs[Boolean]("inactive"),
      //attributes = dbObj.getAs[DBObject]("attributes") map { dbObjToMap(_) }
      attributes = Option(getAttributesFromDBObject(dbObj)).filter(!_.isEmpty)
    )
  }

  class MongoItemsIterator(it: MongoCursor) extends Iterator[Item] {
    def next = dbObjToItem(it.next)
    def hasNext = it.hasNext
  }
}
