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
 * Item object.
 *
 * @param id ID.
 * @param appid App ID that this item belongs to.
 * @param ct Creation time.
 * @param itypes Item types.
 * @param starttime The start time when this item becomes valid.
 * @param endtime The end time when this item becomes invalid.
 * @param price Price of this item.
 * @param profit Net profit made by this item.
 * @param latlng Geolocation of this item.
 * @param inactive Whether to disregard this item during any computation.
 * @param attributes Attributes associated with this item.
 */
case class Item(
  id: String,
  appid: Int,
  ct: DateTime,
  itypes: Seq[String],
  starttime: Option[DateTime],
  endtime: Option[DateTime],
  price: Option[Double] = None,
  profit: Option[Double] = None,
  latlng: Option[Tuple2[Double, Double]] = None,
  inactive: Option[Boolean] = None,
  attributes: Option[Map[String, Any]] = None)

/**
 * Base trait for implementations that interact with items in the backend app
 * data store.
 */
trait Items {
  /** Insert a new item. */
  def insert(item: Item): Unit

  /** Get an item by ID. */
  def get(appid: Int, id: String): Option[Item]

  /** Find all items by App ID. */
  def getByAppid(appid: Int): Iterator[Item]

  /** Find items by App ID sorted by geolocation distance. */
  def getByAppidAndLatlng(appid: Int, latlng: Tuple2[Double, Double],
    within: Option[Double], unit: Option[String]): Iterator[Item]

  /** Find items by App ID which belong to one of the itypes. */
  def getByAppidAndItypes(appid: Int, itypes: Seq[String]): Iterator[Item]

  /** Find current items by App ID which belong to one of the itypes. */
  def getByAppidAndItypesAndTime(appid: Int,
    optItypes: Option[Seq[String]] = None,
    optTime: Option[DateTime] = None): Iterator[Item]

  /**
   * Get items by IDs. Items returned are not guaranteed to be in the same order
   * as the input, as some IDs may not be valid.
   */
  def getByIds(appid: Int, ids: Seq[String]): Seq[Item]

  /** Get items by IDs sorted by their start time in descending order. */
  def getRecentByIds(appid: Int, ids: Seq[String]): Seq[Item]

  /** Update an item. */
  def update(item: Item): Unit

  /** Delete an item. */
  def delete(appid: Int, id: String): Unit

  /** Delete an item. */
  def delete(item: Item): Unit

  /** Delete all items by App ID */
  def deleteByAppid(appid: Int): Unit

  /** count number of records by App ID*/
  def countByAppid(appid: Int): Long
}

class ItemSerializer extends CustomSerializer[Item](format => (
  {
    case JObject(fields) =>
      val seed = Item(
        id = "",
        appid = 0,
        ct = DateTime.now,
        itypes = Seq(),
        starttime = None,
        endtime = None,
        price = None,
        profit = None,
        latlng = None,
        inactive = None,
        attributes = None)
      fields.foldLeft(seed) { case (item, field) =>
        field match {
          case JField("id", JString(id)) => item.copy(id = id)
          case JField("appid", JInt(appid)) => item.copy(appid = appid.intValue)
          case JField("ct", JString(ct)) => item.copy(
            ct = Utils.stringToDateTime(ct))
          case JField("itypes", JArray(s)) => item.copy(itypes = s.map(t =>
            t match {
              case JString(itype) => itype
              case _ => ""
            }
          ))
          case JField("starttime", JString(starttime)) => item.copy(
            starttime = Some(Utils.stringToDateTime(starttime)))
          case JField("endtime", JString(endtime)) => item.copy(
            endtime = Some(Utils.stringToDateTime(endtime)))
          case JField("price", JDouble(price)) => item.copy(price = Some(price))
          case JField("profit", JDouble(profit)) => item.copy(
            profit = Some(profit))
          case JField("latlng", JString(latlng)) => item.copy(
            latlng = Some(latlng.split(',') match {
              case Array(lat, lng) => (lat.toDouble, lng.toDouble) }))
          case JField("inactive", JBool(inactive)) => item.copy(
            inactive = Some(inactive))
          case JField("attributes", JObject(attributes)) => item.copy(
            attributes = Some(Utils.removePrefixFromAttributeKeys(
              attributes.map(x =>
                x match {
                  case JField(k, JString(v)) => k -> v
                }).toMap)))
          case _ => item
        }
      }
  },
  {
    case item: Item =>
      JObject(
        JField("id", JString(item.id)) ::
        JField("appid", JInt(item.appid)) ::
        JField("ct", JString(item.ct.toString)) ::
        JField("itypes", JArray(item.itypes.map(x => JString(x)).toList)) ::
        JField("starttime", item.starttime.map(x =>
          JString(x.toString)).getOrElse(JNothing)) ::
        JField("endtime", item.endtime.map(x =>
          JString(x.toString)).getOrElse(JNothing)) ::
        JField("price", item.price.map(x => JDouble(x)).getOrElse(JNothing)) ::
        JField("profit", item.profit.map(x =>
          JDouble(x)).getOrElse(JNothing)) ::
        JField("latlng", item.latlng.map(x =>
          JString(x._1 + "," + x._2)).getOrElse(JNothing)) ::
        JField("inactive", item.inactive.map(x =>
          JBool(x)).getOrElse(JNothing)) ::
        JField("attributes", item.attributes.map(x =>
          JObject(Utils.addPrefixToAttributeKeys(x).map(y =>
            JField(y._1, JString(y._2.toString))).toList)).
          getOrElse(JNothing)) ::
        Nil)
  }
))
