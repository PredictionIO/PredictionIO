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

import io.prediction.data.storage.{ User, Users }

/** MongoDB implementation of Users. */
class MongoUsers(client: MongoClient, dbName: String) extends Users {
  private val db = client(dbName)
  private val emptyObj = MongoDBObject()
  private val userColl = db("users")

  RegisterJodaTimeConversionHelpers()

  def insert(user: User) = {
    val id = MongoDBObject("_id" -> idWithAppid(user.appid, user.id))
    val appid = MongoDBObject("appid" -> user.appid)
    val ct = MongoDBObject("ct" -> user.ct)
    val lnglat = user.latlng map { l => MongoDBObject("lnglat" -> MongoDBList(l._2, l._1)) } getOrElse emptyObj
    val inactive = user.inactive map { i => MongoDBObject("inactive" -> i) } getOrElse emptyObj
    //val attributes = user.attributes map { a => MongoDBObject("attributes" -> a) } getOrElse emptyObj
    // add "ca_" prefix for custom attributes
    val attributes = user.attributes map { a => attributesToMongoDBObject(a) } getOrElse emptyObj
    userColl.save(id ++ appid ++ ct ++ lnglat ++ inactive ++ attributes)
  }

  def get(appid: Int, id: String) = userColl.findOne(MongoDBObject("_id" -> idWithAppid(appid, id))) map { dbObjToUser(_) }

  def getByAppid(appid: Int) = new MongoUsersIterator(userColl.find(MongoDBObject("appid" -> appid)))

  def update(user: User) = {
    val id = MongoDBObject("_id" -> idWithAppid(user.appid, user.id))
    val appid = MongoDBObject("appid" -> user.appid)
    val ct = MongoDBObject("ct" -> user.ct)
    val lnglat = user.latlng map { l => MongoDBObject("lnglat" -> MongoDBList(l._2, l._1)) } getOrElse emptyObj
    val inactive = user.inactive map { i => MongoDBObject("inactive" -> i) } getOrElse emptyObj
    //val attributes = user.attributes map { a => MongoDBObject("attributes" -> a) } getOrElse emptyObj
    val attributes = user.attributes map { a => attributesToMongoDBObject(a) } getOrElse emptyObj
    userColl.update(id, id ++ appid ++ ct ++ lnglat ++ inactive ++ attributes)
  }

  def delete(appid: Int, id: String) = userColl.remove(MongoDBObject("_id" -> idWithAppid(appid, id)))
  def delete(user: User) = delete(user.appid, user.id)

  def deleteByAppid(appid: Int): Unit = {
    userColl.remove(MongoDBObject("appid" -> appid))
  }

  def countByAppid(appid: Int): Long = userColl.count(MongoDBObject("appid" -> appid))

  private def dbObjToUser(dbObj: DBObject) = {
    val appid = dbObj.as[Int]("appid")
    User(
      id = dbObj.as[String]("_id").drop(appid.toString.length + 1),
      appid = appid,
      ct = dbObj.as[DateTime]("ct"),
      latlng = dbObj.getAs[MongoDBList]("lnglat") map { lnglat => (lnglat(1).asInstanceOf[Double], lnglat(0).asInstanceOf[Double]) },
      inactive = dbObj.getAs[Boolean]("inactive"),
      //attributes = dbObj.getAs[DBObject]("attributes") map { dbObjToMap(_) }
      attributes = Option(getAttributesFromDBObject(dbObj)).filter(!_.isEmpty)
    )
  }

  class MongoUsersIterator(it: MongoCursor) extends Iterator[User] {
    def next = dbObjToUser(it.next)
    def hasNext = it.hasNext
  }
}
