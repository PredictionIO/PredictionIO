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
 * User object.
 *
 * @param id ID.
 * @param appid App ID that this user belongs to.
 * @param ct Creation time.
 * @param latlng Geolocation of this user.
 * @param inactive Whether to disregard this user during any computation.
 * @param attributes Attributes associated with this user.
 */
case class User(
  id: String,
  appid: Int,
  ct: DateTime,
  latlng: Option[Tuple2[Double, Double]] = None,
  inactive: Option[Boolean] = None,
  attributes: Option[Map[String, Any]] = None)

/** Base trait for implementations that interact with users in the backend app data store. */
trait Users {
  /** Insert a new user. */
  def insert(user: User): Unit

  /** Find a user by ID. */
  def get(appid: Int, id: String): Option[User]

  /** Find all users by App ID. */
  def getByAppid(appid: Int): Iterator[User]

  /** Update a user. */
  def update(user: User): Unit

  /** Delete a user. */
  def delete(appid: Int, id: String): Unit

  /** Delete a user. */
  def delete(user: User): Unit

  /** Delete all users by App ID */
  def deleteByAppid(appid: Int): Unit

  /** count number of records by App ID*/
  def countByAppid(appid: Int): Long

}

class UserSerializer extends CustomSerializer[User](format => (
  {
    case JObject(fields) =>
      val seed = User(
        id = "",
        appid = 0,
        ct = DateTime.now,
        latlng = None,
        inactive = None,
        attributes = None)
      fields.foldLeft(seed) { case (user, field) =>
        field match {
          case JField("id", JString(id)) => user.copy(id = id)
          case JField("appid", JInt(appid)) => user.copy(appid = appid.intValue)
          case JField("ct", JString(ct)) => user.copy(
            ct = Utils.stringToDateTime(ct))
          case JField("latlng", JString(latlng)) => user.copy(
            latlng = Some(latlng.split(',') match {
              case Array(lat, lng) => (lat.toDouble, lng.toDouble) }))
          case JField("inactive", JBool(inactive)) => user.copy(
            inactive = Some(inactive))
          case JField("attributes", JObject(attributes)) => user.copy(
            attributes = Some(Utils.removePrefixFromAttributeKeys(
              attributes.map(x =>
                x match {
                  case JField(k, JString(v)) => k -> v
                }).toMap)))
          case _ => user
        }
      }
  },
  {
    case user: User =>
      JObject(
        JField("id", JString(user.id)) ::
        JField("appid", JInt(user.appid)) ::
        JField("ct", JString(user.ct.toString)) ::
        JField("latlng", user.latlng.map(x =>
          JString(x._1 + "," + x._2)).getOrElse(JNothing)) ::
        JField("inactive", user.inactive.map(x =>
          JBool(x)).getOrElse(JNothing)) ::
        JField("attributes", user.attributes.map(x =>
          JObject(Utils.addPrefixToAttributeKeys(x).map(y =>
            JField(y._1, JString(y._2.toString))).toList)).
          getOrElse(JNothing)) ::
        Nil)
  }
))
