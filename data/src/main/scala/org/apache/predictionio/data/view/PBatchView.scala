/** Copyright 2015 TappingStone, Inc.
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

package org.apache.predictionio.data.view

import org.apache.predictionio.data.storage.hbase.HBPEvents
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.EventValidation
import org.apache.predictionio.data.storage.DataMap
import org.apache.predictionio.data.storage.Storage

import org.joda.time.DateTime

import org.json4s.JValue

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD


// each JValue data associated with the time it is set
private[predictionio] case class PropTime(val d: JValue, val t: Long) extends Serializable

private[predictionio] case class SetProp (
  val fields: Map[String, PropTime],
  // last set time. Note: fields could be empty with valid set time
  val t: Long) extends Serializable {

  def ++ (that: SetProp): SetProp = {
    val commonKeys = fields.keySet.intersect(that.fields.keySet)

    val common: Map[String, PropTime] = commonKeys.map { k =>
      val thisData = this.fields(k)
      val thatData = that.fields(k)
      // only keep the value with latest time
      val v = if (thisData.t > thatData.t) thisData else thatData
      (k, v)
    }.toMap

    val combinedFields = common ++
      (this.fields -- commonKeys) ++ (that.fields -- commonKeys)

    // keep the latest set time
    val combinedT = if (this.t > that.t) this.t else that.t

    SetProp(
      fields = combinedFields,
      t = combinedT
    )
  }
}

private[predictionio] case class UnsetProp (fields: Map[String, Long]) extends Serializable {
  def ++ (that: UnsetProp): UnsetProp = {
    val commonKeys = fields.keySet.intersect(that.fields.keySet)

    val common: Map[String, Long] = commonKeys.map { k =>
      val thisData = this.fields(k)
      val thatData = that.fields(k)
      // only keep the value with latest time
      val v = if (thisData > thatData) thisData else thatData
      (k, v)
    }.toMap

    val combinedFields = common ++
      (this.fields -- commonKeys) ++ (that.fields -- commonKeys)

    UnsetProp(
      fields = combinedFields
    )
  }
}

private[predictionio] case class DeleteEntity (t: Long) extends Serializable {
  def ++ (that: DeleteEntity): DeleteEntity = {
    if (this.t > that.t) this else that
  }
}

private[predictionio] case class EventOp (
  val setProp: Option[SetProp] = None,
  val unsetProp: Option[UnsetProp] = None,
  val deleteEntity: Option[DeleteEntity] = None
) extends Serializable {

  def ++ (that: EventOp): EventOp = {
    EventOp(
      setProp = (setProp ++ that.setProp).reduceOption(_ ++ _),
      unsetProp = (unsetProp ++ that.unsetProp).reduceOption(_ ++ _),
      deleteEntity = (deleteEntity ++ that.deleteEntity).reduceOption(_ ++ _)
    )
  }

  def toDataMap(): Option[DataMap] = {
    setProp.flatMap { set =>

      val unsetKeys: Set[String] = unsetProp.map( unset =>
        unset.fields.filter{ case (k, v) => (v >= set.fields(k).t) }.keySet
      ).getOrElse(Set())

      val combinedFields = deleteEntity.map { delete =>
        if (delete.t >= set.t) {
          None
        } else {
          val deleteKeys: Set[String] = set.fields
            .filter { case (k, PropTime(kv, t)) =>
              (delete.t >= t)
            }.keySet
          Some(set.fields -- unsetKeys -- deleteKeys)
        }
      }.getOrElse{
        Some(set.fields -- unsetKeys)
      }

      // Note: mapValues() doesn't return concrete Map and causes
      // NotSerializableException issue. Use map(identity) to work around this.
      // see https://issues.scala-lang.org/browse/SI-7005
      combinedFields.map(f => DataMap(f.mapValues(_.d).map(identity)))
    }
  }

}

private[predictionio] object EventOp {
  def apply(e: Event): EventOp = {
    val t = e.eventTime.getMillis
    e.event match {
      case "$set" => {
        val fields = e.properties.fields.mapValues(jv =>
          PropTime(jv, t)
        ).map(identity)

        EventOp(
          setProp = Some(SetProp(fields = fields, t = t))
        )
      }
      case "$unset" => {
        val fields = e.properties.fields.mapValues(jv => t).map(identity)
        EventOp(
          unsetProp = Some(UnsetProp(fields = fields))
        )
      }
      case "$delete" => {
        EventOp(
          deleteEntity = Some(DeleteEntity(t))
        )
      }
      case _ => {
        EventOp()
      }
    }
  }
}

@deprecated("Use PEvents or PEventStore instead.", "0.9.2")
class PBatchView(
  val appId: Int,
  val startTime: Option[DateTime],
  val untilTime: Option[DateTime],
  val sc: SparkContext) {

  // NOTE: parallel Events DB interface
  @transient lazy val eventsDb = Storage.getPEvents()

  @transient lazy val _events: RDD[Event] =
    eventsDb.getByAppIdAndTimeAndEntity(
      appId = appId,
      startTime = startTime,
      untilTime = untilTime,
      entityType = None,
      entityId = None)(sc)

  // TODO: change to use EventSeq?
  @transient lazy val events: RDD[Event] = _events

  def aggregateProperties(
    entityType: String,
    startTimeOpt: Option[DateTime] = None,
    untilTimeOpt: Option[DateTime] = None
  ): RDD[(String, DataMap)] = {

    _events
      .filter( e => ((e.entityType == entityType) &&
        (EventValidation.isSpecialEvents(e.event))) )
      .map( e => (e.entityId, EventOp(e) ))
      .aggregateByKey[EventOp](EventOp())(
        // within same partition
        seqOp = { case (u, v) => u ++ v },
        // across partition
        combOp = { case (accu, u) => accu ++ u }
      )
      .mapValues(_.toDataMap)
      .filter{ case (k, v) => v.isDefined }
      .map{ case (k, v) => (k, v.get) }
  }

}
