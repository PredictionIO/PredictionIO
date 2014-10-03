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
import org.apache.hadoop.io._
import scala.collection.JavaConversions._

package object stock {
  type DailyTuple = Tuple8[DateTime, Double, Double, Double, Double, Double, Double, Boolean]
}

/**
 * ItemTrend object.
 *
 * For now, there is only one time series attached to an ItemTrend object. We
 * expect to add more, e.g. active, volume, etc..
 *
 * @param id ID.
 * @param appid App ID that this itemTrend belongs to.
 * @param ct Creation time.
 * @param basetime Base time for the time series
 * @param prices A time series for price.
 * @param actives A time series for activeness. Set to false if field is
 * missing.
 */
case class ItemTrend(

  id: String,
  appid: Int,
  ct: DateTime,
  //priceRaw: Seq[(DateTime, Double)] = List[(DateTime, Double)](),

  // Date, Open, High Low, Close, Volume, Adj.Close, Active
  // The active field is true if data originally not exists.
  daily: Seq[stock.DailyTuple] = Vector[stock.DailyTuple]())

/**
 * Base trait for implementations that interact with itemTrendTrends in the
 * backend app data store.
 */
trait ItemTrends {
  /** Insert a new itemTrendTrend. */
  def insert(itemTrendTrend: ItemTrend): Unit

  /** Get an itemTrendTrend by ID. */
  def get(appid: Int, id: String): Option[ItemTrend]

  /** Check if itemTrendTrend by ID exists. */
  def contains(appid: Int, id: String): Boolean

  /** Find all itemTrendTrends by App ID. */
  def getByAppid(appid: Int): Iterator[ItemTrend]

  /** Get itemTrendTrends by IDs. */
  def getByIds(appid: Int, ids: Seq[String]): Seq[ItemTrend]

  /** Update an itemTrend. */
  //def update(itemTrend: ItemTrend): Unit

  /*
  def appendPrice(appid: Int, id: String, t: DateTime, p: Double): Unit

  def extendPrice(appid: Int, id: String, newPrice: Seq[(DateTime, Double)]): Unit
  */

  /** Delete an itemTrend. */
  def delete(appid: Int, id: String): Unit

  /** Delete an itemTrend. */
  def delete(itemTrend: ItemTrend): Unit

  /** Delete all itemTrendTrends by App ID */
  def deleteByAppid(appid: Int): Unit

  /** count number of records by App ID*/
  def countByAppid(appid: Int): Long
}

object ItemTrendSerializer {
  def apply(mapWritable: MapWritable): ItemTrend = {
    val seed = ItemTrend(id = "", appid = 0, ct = DateTime.now)

    mapWritable.entrySet.foldLeft(seed) { case (itemtrend, field) => {
      val key: String = field.getKey.asInstanceOf[Text].toString
      val value: Writable = field.getValue
      key match {
        case "appid" => {
          itemtrend.copy(appid = value.asInstanceOf[LongWritable].get.toInt)
        }
        case "id" => {
          itemtrend.copy(id = value.asInstanceOf[Text].toString)
        }
        case "ct" => {
          val ctStr: String = value.asInstanceOf[Text].toString
          val ct: DateTime = Utils.stringToDateTime(ctStr)
          itemtrend.copy(ct = ct)
        }
        case "daily" => {
          val array: Array[Writable] = value.asInstanceOf[ArrayWritable].get
          val daily = array.map { d => {
            val m: Map[String, Writable] = d.asInstanceOf[MapWritable]
              .entrySet
              .map(kv => (kv.getKey.toString, kv.getValue))
              .toMap
            new stock.DailyTuple(
              Utils.stringToDateTime(m("date").asInstanceOf[Text].toString),
              m("open").asInstanceOf[DoubleWritable].get,
              m("high").asInstanceOf[DoubleWritable].get,
              m("low").asInstanceOf[DoubleWritable].get,
              m("close").asInstanceOf[DoubleWritable].get,
              m("volume").asInstanceOf[DoubleWritable].get,
              m("adjclose").asInstanceOf[DoubleWritable].get,
              m("active").asInstanceOf[BooleanWritable].get)
          }}
          itemtrend.copy(daily = daily)
        }
      }
    }}
  }
}

class ItemTrendSerializer extends CustomSerializer[ItemTrend](formats => (
  {
    case JObject(fields) =>
      val seed = ItemTrend(
        id = "",
        appid = 0,
        ct = DateTime.now)
      fields.foldLeft(seed) { case (itemtrend, field) =>
        field match {
          case JField("id", JString(id)) => itemtrend.copy(id = id)
          case JField("appid", JInt(appid)) => itemtrend.copy(
            appid = appid.intValue)
          case JField("ct", JString(ct)) => itemtrend.copy(
            ct = Utils.stringToDateTime(ct))
          case JField("daily", JArray(d)) => itemtrend.copy(daily =
            d map { _ match {
              case JObject(List(
                JField("date", JString(date)),
                JField("open", JDouble(open)),
                JField("high", JDouble(high)),
                JField("low", JDouble(low)),
                JField("close", JDouble(close)),
                JField("volume", JDouble(volume)),
                JField("adjclose", JDouble(adjclose)),
                JField("active", JBool(active)))) => new stock.DailyTuple(
                Utils.stringToDateTime(date),
                open,
                high,
                low,
                close,
                volume,
                adjclose,
                active)
              }
            }
          )
        }
      }
  },
  {
    case itemtrend: ItemTrend =>
      JObject(
        JField("id", JString(itemtrend.id)) ::
        JField("appid", JInt(itemtrend.appid)) ::
        JField("ct", JString(itemtrend.ct.toString)) ::
        JField("daily", JArray(itemtrend.daily.map(d =>
          JObject(List(
            JField("date", JString(d._1.toString)),
            JField("open", JDouble(d._2)),
            JField("high", JDouble(d._3)),
            JField("low", JDouble(d._4)),
            JField("close", JDouble(d._5)),
            JField("volume", JDouble(d._6)),
            JField("adjclose", JDouble(d._7)),
            JField("active", JBool(d._8))))).toList)) :: Nil)
  }
))
