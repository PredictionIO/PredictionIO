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

package io.prediction.data.storage

import com.github.nscala_time.time.Imports._
import org.json4s._

/**
 * User-to-item action object.
 *
 * @param appid App ID that this item belongs to.
 * @param action Type of this action.
 * @param uid User ID of this action.
 * @param iid Item ID of this action.
 * @param t Time of this action.
 * @param latlng Geolocation of this action.
 * @param v The value of this action (if applicable).
 * @param price Price associated with this action (if applicable).
 */
case class U2IAction(
  appid: Int,
  action: String,
  uid: String,
  iid: String,
  t: DateTime,
  latlng: Option[Tuple2[Double, Double]] = None,
  v: Option[Int] = None,
  price: Option[Double] = None)

/** Base trait for implementations that interact with user-to-item actions in the backend app data store. */
trait U2IActions {

  /** Inserts a user-to-item action. */
  def insert(u2iAction: U2IAction): Unit

  /** Gets all user-to-item actions by App ID. */
  def getAllByAppid(appid: Int): Iterator[U2IAction]

  /** Gets by appid where t >= start and t < untilTime */
  def getByAppidAndTime(appid: Int, startTime: DateTime, untilTime: DateTime):
    Iterator[U2IAction]

  /** Gets all user-to-item actions by App ID, User ID, and Item IDs. */
  def getAllByAppidAndUidAndIids(appid: Int, uid: String, iids: Seq[String]): Iterator[U2IAction]

  /** Get all users-to-item actions by AppID, Item ID and optionally sort by User ID */
  def getAllByAppidAndIid(appid: Int, iid: String, sortedByUid: Boolean = true): Iterator[U2IAction]

  /** Delete all user-to-item actions by App ID */
  def deleteByAppid(appid: Int): Unit

  /** count number of records by App ID*/
  def countByAppid(appid: Int): Long
}

class U2IActionSerializer extends CustomSerializer[U2IAction](format => (
  {
    case JObject(fields) =>
      val seed = U2IAction(
        appid = 0,
        action = "",
        uid = "",
        iid = "",
        t = DateTime.now,
        latlng = None,
        v = None,
        price = None)
      fields.foldLeft(seed) { case (u2iaction, field) =>
        field match {
          case JField("appid", JInt(appid)) => u2iaction.copy(
            appid = appid.intValue)
          case JField("action", JString(action)) => u2iaction.copy(
            action = action)
          case JField("uid", JString(uid)) => u2iaction.copy(uid = uid)
          case JField("iid", JString(iid)) => u2iaction.copy(iid = iid)
          case JField("t", JString(t)) => u2iaction.copy(
            t = Utils.stringToDateTime(t))
          case JField("latlng", JString(latlng)) => u2iaction.copy(
            latlng = Some(latlng.split(',') match {
              case Array(lat, lng) => (lat.toDouble, lng.toDouble) }))
          case JField("v", JInt(v)) => u2iaction.copy(v = Some(v.intValue))
          case JField("price", JDouble(price)) => u2iaction.copy(
            price = Some(price))
          case _ => u2iaction
        }
      }
  },
  {
    case u2iaction: U2IAction =>
      JObject(
        JField("appid", JInt(u2iaction.appid)) ::
        JField("action", JString(u2iaction.action)) ::
        JField("uid", JString(u2iaction.uid)) ::
        JField("iid", JString(u2iaction.iid)) ::
        JField("t", JString(u2iaction.t.toString)) ::
        JField("latlng", u2iaction.latlng.map(x =>
          JString(x._1 + "," + x._2)).getOrElse(JNothing)) ::
        JField("v", u2iaction.v.map(JInt(_)).getOrElse(JNothing)) ::
        JField("price", u2iaction.price.map(JDouble(_)).getOrElse(JNothing)) ::
        Nil)
  }
))
