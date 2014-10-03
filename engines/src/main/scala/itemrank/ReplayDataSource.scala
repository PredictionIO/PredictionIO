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

package io.prediction.engines.itemrank

// This module allows users to evaluate their algorithm data with their acutal
// data. It takes the mongodb dump (from Version 0.7 or before) of the three key
// collections: User, Item, and U2IActions. It evaluates the data in a
// time-rolling fashsion. First, it sets a cutoff day d and uses the data before
// it for training, and then use the following n days for testing. Then, it
// shifts the cutoff day by n, i.e. using data before (d + n-days) for training,
// then use data between d + n and d + 2n for testing, and so on until the last
// day.
//
// In each test, we construct a query using a combination of 1. all conversion
// data of a user on a day; and 2. the global top x sold items of that day.
//
// Notice that this replay may not be completely accurate as User and Item are
// not event log.

import io.prediction.controller._
import io.prediction.controller.{ Params => BaseParams }
import com.github.nscala_time.time.Imports._
import org.joda.time.Instant
import org.json4s._
import org.json4s.native.JsonMethods._
import scala.io.Source
import scala.collection.immutable.HashMap
import scala.util.hashing.MurmurHash3

import io.prediction.engines.base.ItemTD
import io.prediction.engines.base.UserTD
import io.prediction.engines.base.U2IActionTD
import io.prediction.engines.base.TrainingData

case class ReplaySliceParams(
  val name: String,
  val idx: Int
) extends Serializable with HasName


object ReplayDataSource {
  case class Params(
    val userPath: String,
    val itemPath: String,
    val u2iPath: String,
    val baseDate: LocalDate,
    val fromIdx: Int,
    val untilIdx: Int,
    val testingWindowSize: Int,
    // Mix top x sold items in Query
    val numTopSoldItems: Int,
    // Only items belonging to this whitelist is considered.
    val whitelistItypes: Seq[String],
    // Only u2i belonging to this single action is considered.
    val whitelistAction: String
  ) extends BaseParams


  case class PreprocessedData(
    val userList: Array[User],
    val itemList: Array[Item],
    val date2u2iList: Map[LocalDate, Array[U2I]]
  ) extends Serializable {
    val ui2UserTd: Map[Int, UserTD] = userList
      .zipWithIndex
      .map(_.swap)
      .toMap
      .mapValues(user => new UserTD(user._id))
    val ui2uid: Map[Int, String] = ui2UserTd.mapValues(_.uid)
    val uid2ui: Map[String, Int] = ui2uid.map(_.swap)

    val ii2ItemTd: Map[Int, ItemTD] = itemList
      .zipWithIndex
      .map(_.swap)
      .toMap
      .mapValues(
        item => new ItemTD(item._id, item.itypes.toSeq, None, None, false))
    val ii2iid: Map[Int, String] = ii2ItemTd.mapValues(_.iid)
    val iid2ii: Map[String, Int] = ii2iid.map(_.swap)

    val date2ActionTds: Map[LocalDate, Array[U2IActionTD]] = date2u2iList
      .mapValues(
        _.map(u2i => new U2IActionTD(
          uid2ui(u2i.uid),
          iid2ii(u2i.iid),
          u2i.action,
          None,
          u2i.t.$date)))

    val dailyServedItems: Map[LocalDate, Array[(String, Int)]] = date2u2iList
      .mapValues(
        _.map(_.iid).groupBy(identity).mapValues(_.size).toArray.sortBy(-_._2)
      )
  }
}

class ReplayDataSource(val dsp: ReplayDataSource.Params)
  extends LDataSource[
      ReplayDataSource.Params, ReplaySliceParams, TrainingData, Query, Actual] {


  def load(): (Array[User], Array[Item], Array[U2I]) = {
    implicit val formats = DefaultFormats

    val u2iList = Source
      .fromFile(dsp.u2iPath).getLines
      .map { s => parse(s).extract[U2I] }
      .toArray

    val userList = Source
      .fromFile(dsp.userPath).getLines
      .map { s => parse(s).extract[User] }
      .toArray

    val itemList = Source
      .fromFile(dsp.itemPath)
      .getLines
      .map { s => parse(s).extract[Item] }
      .toArray

    return (userList, itemList, u2iList)
  }

  def preprocess(input: (Array[User],  Array[Item], Array[U2I]))
  : ReplayDataSource.PreprocessedData = {
    val (users, items, u2is) = input

    val whitelistItypeSet = Set(dsp.whitelistItypes:_*)
    val validItems: Array[Item] = items
      .filter(_.itypes.find(it => whitelistItypeSet(it)) != None)
    val validIidSet: Set[String] = validItems.map(_._id).toSet

    val date2Actions: Map[LocalDate, Array[U2I]] = u2is
      .filter(u2i => validIidSet(u2i.iid))
      .filter(u2i => u2i.action == dsp.whitelistAction)
      .groupBy(_.dt.toLocalDate)

    ReplayDataSource.PreprocessedData(users, validItems, date2Actions)
  }

  def generateParams(): Seq[ReplaySliceParams] = {
    Range(dsp.fromIdx, dsp.untilIdx, dsp.testingWindowSize).map { idx =>
      val trainingUntilDate: LocalDate = dsp.baseDate.plusDays(idx)
      val dow = trainingUntilDate.dayOfWeek.getAsShortText
      ReplaySliceParams(
        name = s"${trainingUntilDate.toString()} $dow",
        idx = idx)
    }
  }

  def generateOne(input: (ReplayDataSource.PreprocessedData, ReplaySliceParams))
  : (ReplaySliceParams, TrainingData, Array[(Query, Actual)]) = {
    val (data, dp) = input

    val userList: Array[User] = data.userList
    val itemList: Array[Item] = data.itemList
    val date2u2iList: Map[LocalDate, Array[U2I]] = data.date2u2iList

    val ui2UserTd = data.ui2UserTd
    val ui2uid = data.ui2uid
    val uid2ui = data.uid2ui

    val ii2ItemTd = data.ii2ItemTd
    val ii2iid = data.ii2iid
    val iid2ii = data.iid2ii

    val date2Actions = data.date2ActionTds
    val dailyServedItems = data.dailyServedItems

    val trainingUntilDate: LocalDate = dsp.baseDate.plusDays(dp.idx)
    println("TrainingUntil: " + trainingUntilDate.toString)

    val trainingDate2Actions: Map[LocalDate, Array[U2IActionTD]] =
      date2Actions.filterKeys(k => k.isBefore(trainingUntilDate))

    val trainingActions: Array[U2IActionTD] = trainingDate2Actions
      .values
      .flatMap(_.toSeq)
      .toArray

    val trainingData = new TrainingData(
      HashMap[Int, UserTD]() ++ ui2UserTd,
      HashMap[Int, ItemTD]() ++ ii2ItemTd,
      Array[U2IActionTD]() ++ trainingActions)

    val uiActionsMap: Map[Int, Int] = trainingActions
      .groupBy(_.uindex)
      .mapValues(_.size)

    // Seq[(Int, Int)]: (User, Order Size)
    val date2OrderSizeMap: Map[LocalDate, Array[(Int, Int)]] =
    trainingDate2Actions
      .mapValues {
        _.groupBy(_.uindex).mapValues(_.size).toArray
      }

    val uiAverageSizeMap: Map[Int, Double] = date2OrderSizeMap
      .values
      .flatMap(_.toSeq)
      .groupBy(_._1)
      .mapValues( l => l.map(_._2).sum.toDouble / l.size )

    val uiPreviousOrdersMap: Map[Int, Int] = date2OrderSizeMap
      .values
      .flatMap(_.toSeq)
      .groupBy(_._1)
      .mapValues(_.size)

    val uiVarietyMap: Map[Int, Int] = trainingActions
      .groupBy(_.uindex)
      .mapValues(_.map(_.iindex).distinct.size)

    val queryActionList: Array[(Query, Actual)] =
    Range(dp.idx, math.min(dp.idx + dsp.testingWindowSize, dsp.untilIdx))
    .map { queryIdx => dsp.baseDate.plusDays(queryIdx) }
    .flatMap { queryDate => {
      //println(
      //  s"Testing: ${queryDate.toString} DOW(${queryDate.getDayOfWeek})")
      val u2is = date2u2iList.getOrElse(queryDate, Array[U2I]())
      val uid2Actions: Map[String, Array[U2I]] = u2is.groupBy(_.uid)

      val user2iids = uid2Actions.mapValues(_.map(_.iid))
      // Use first action time.
      val user2LocalDT = uid2Actions
        .mapValues(_.map(_.dt.toLocalDateTime).min)

      val todayItems: Seq[String] = dailyServedItems(queryDate)
        .take(dsp.numTopSoldItems)
        .map(_._1)

      user2iids.map { case (uid, iids) => {
        val possibleIids = (iids ++ todayItems)
          .distinct

        //val sortedIids = random.shuffle(possibleIids)
        // Introduce some kind of stable randomness
        val sortedIids = possibleIids
          .sortBy(iid => MurmurHash3.stringHash(iid))
        //val sortedIids = possibleIids.sortBy(identity)

        val query = new Query(uid, sortedIids)
        val ui = uid2ui(uid)

        val actual = new Actual(
          iids = iids.toSeq,
          previousActionCount = uiActionsMap.getOrElse(ui, 0),
          localDate = queryDate,
          localDateTime = user2LocalDT(uid),
          averageOrderSize = uiAverageSizeMap.getOrElse(ui, 0),
          previousOrders = uiPreviousOrdersMap.getOrElse(ui, 0),
          variety = uiVarietyMap.getOrElse(ui, 0)
        )
        (query, actual)
      }}
    }}
    .toArray

    //println("Testing Size: " + queryActionList.size)
    (dp, trainingData, queryActionList)
  }


  def generate(input: ReplayDataSource.PreprocessedData)
  : Seq[(ReplaySliceParams, TrainingData, Array[(Query, Actual)])] = {

    val paramsList = generateParams()

    paramsList.map { params => {
      generateOne((input, params))
    }}
  }

  override
  def read(): Seq[(ReplaySliceParams, TrainingData, Seq[(Query, Actual)])] = {

    generate(preprocess(load()))
    .map(e => (e._1, e._2, e._3.toSeq))
  }
}

case class DateObject(val $date: Long)

case class U2I(
  val action: String,
  val uid: String, val iid: String, val t: DateObject) {
  lazy val dt: DateTime =
    new DateTime(new Instant(t.$date), DateTimeZone.forOffsetHours(-8))

  override def toString(): String =
    s"U2I($uid, $iid, $action, $dt)"
}

case class User(val _id: String)

case class Item(val _id: String, val starttime: DateObject,
  val itypes: Array[String],
  val ca_name: String) {
  override def toString(): String =
    s"${_id} $ca_name [" + itypes.mkString(",") + "]"

  val itypesSet = Set(itypes:_*)
}
