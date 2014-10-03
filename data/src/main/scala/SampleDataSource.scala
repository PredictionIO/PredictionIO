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

package io.prediction.data.sample

import io.prediction.data.storage.Events
import io.prediction.data.storage.Storage
import io.prediction.data.view.LBatchView

import org.joda.time.DateTime

// engine's Data
class ItemTD(
  val iid: String,
  val itypes: Seq[String],
  val starttime: Option[Long],
  val endtime: Option[Long],
  val inactive: Boolean) extends Serializable {
    override def toString = s"${iid} ${itypes} ${starttime} ${endtime}" +
      s" ${inactive}"
  }

class UserTD(
  val uid: String
) extends Serializable {
  override def toString = s"${uid}"
}

class U2IActionTD(
  val uid: String,
  val iid: String,
  val action: String, // action name
  val v: Option[Int],
  val t: Long // action time
) extends Serializable {
  override def toString = s"${uid} ${iid} ${action} ${v} ${t}"
}

class TrainingData(
  val users: Map[String, UserTD], // uindex->uid
  val items: Map[String, ItemTD], // iindex->itemTD
  val u2iActions: Seq[U2IActionTD]
) extends Serializable {
  override def toString = s"u2iActions: ${u2iActions}\n" +
    s"users: ${users}\n" +
    s"items: ${items}\n"
}

// data source params
case class DataSourceParams(
  val appId: Int,
  val startTime: Option[DateTime],
  val untilTime: Option[DateTime],
  val attributes: AttributeNames
)

// this algorithm require the following properties
case class AttributeNames(
  val user: String,
  val item: String,
  val u2iActions: Set[String], // event name of the u2i actions
  val itypes: String,
  val starttime: String,
  val endtime: String,
  val inactive: String, // boolean inactive
  val rating: String // integer rating
)

class DataSource(val params: DataSourceParams) {

  @transient lazy val batchView = new LBatchView(params.appId,
    params.startTime, params.untilTime)

  def readTraining(): TrainingData = {

    val userMap = batchView.aggregateProperties(params.attributes.user)
    val itemMap = batchView.aggregateProperties(params.attributes.item)

    val users = userMap.map { case (k,v) => (k, new UserTD(uid=k)) }
    val items = itemMap.map { case (k,v) =>
      (k, new ItemTD(
        iid = k,
        itypes = v.getOrElse[List[String]](params.attributes.itypes, List()),
        starttime = v.getOpt[DateTime](params.attributes.starttime)
          .map(_.getMillis),
        endtime = v.getOpt[DateTime](params.attributes.endtime)
          .map(_.getMillis),
        inactive = v.getOrElse[Boolean](params.attributes.inactive, false)
      ))}

    val u2i = batchView.events.filter( e =>
      params.attributes.u2iActions.contains(e.event) )
      .map(e => new U2IActionTD(
        uid = e.entityId,
        iid = e.targetEntityId.get,
        action = e.event,
        v = e.properties.getOpt[Int](params.attributes.rating),
        t = e.eventTime.getMillis
      ))

    new TrainingData(
      users = users,
      items = items,
      u2iActions = u2i
    )
  }

}

object ItemRankDataSource {

  def main(args: Array[String]) {

    val dsp = DataSourceParams(
      appId = args(0).toInt,
      startTime = None,
      untilTime = None,
      attributes = AttributeNames(
        user = "user",
        item = "item",
        u2iActions = Set("rate", "view"),
        itypes = "pio_itypes",
        starttime = "starttime",
        endtime = "endtime",
        inactive = "inactive",
        rating = "pio_rating"
      )
    )

    val dataSource = new DataSource(dsp)
    val td = dataSource.readTraining()

    println(td)
  }

}
