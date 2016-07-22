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

package org.apache.predictionio.data.storage

import org.joda.time.DateTime

import org.json4s.JValue

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

// each JValue data associated with the time it is set
private[predictionio] case class PropTime(val d: JValue, val t: Long)
    extends Serializable

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

private[predictionio] case class UnsetProp (fields: Map[String, Long])
    extends Serializable {
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
  val deleteEntity: Option[DeleteEntity] = None,
  val firstUpdated: Option[DateTime] = None,
  val lastUpdated: Option[DateTime] = None
) extends Serializable {

  def ++ (that: EventOp): EventOp = {
    val firstUp = (this.firstUpdated ++ that.firstUpdated).reduceOption{
      (a, b) => if (b.getMillis < a.getMillis) b else a
    }
    val lastUp = (this.lastUpdated ++ that.lastUpdated).reduceOption {
      (a, b) => if (b.getMillis > a.getMillis) b else a
    }

    EventOp(
      setProp = (setProp ++ that.setProp).reduceOption(_ ++ _),
      unsetProp = (unsetProp ++ that.unsetProp).reduceOption(_ ++ _),
      deleteEntity = (deleteEntity ++ that.deleteEntity).reduceOption(_ ++ _),
      firstUpdated = firstUp,
      lastUpdated = lastUp
    )
  }

  def toPropertyMap(): Option[PropertyMap] = {
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
      combinedFields.map{ f =>
        require(firstUpdated.isDefined,
          "Unexpected Error: firstUpdated cannot be None.")
        require(lastUpdated.isDefined,
          "Unexpected Error: lastUpdated cannot be None.")
        PropertyMap(
          fields = f.mapValues(_.d).map(identity),
          firstUpdated = firstUpdated.get,
          lastUpdated = lastUpdated.get
        )
      }
    }
  }

}

private[predictionio] object EventOp {
  // create EventOp from Event object
  def apply(e: Event): EventOp = {
    val t = e.eventTime.getMillis
    e.event match {
      case "$set" => {
        val fields = e.properties.fields.mapValues(jv =>
          PropTime(jv, t)
        ).map(identity)

        EventOp(
          setProp = Some(SetProp(fields = fields, t = t)),
          firstUpdated = Some(e.eventTime),
          lastUpdated = Some(e.eventTime)
        )
      }
      case "$unset" => {
        val fields = e.properties.fields.mapValues(jv => t).map(identity)
        EventOp(
          unsetProp = Some(UnsetProp(fields = fields)),
          firstUpdated = Some(e.eventTime),
          lastUpdated = Some(e.eventTime)
        )
      }
      case "$delete" => {
        EventOp(
          deleteEntity = Some(DeleteEntity(t)),
          firstUpdated = Some(e.eventTime),
          lastUpdated = Some(e.eventTime)
        )
      }
      case _ => {
        EventOp()
      }
    }
  }
}


private[predictionio] object PEventAggregator {

  val eventNames = List("$set", "$unset", "$delete")

  def aggregateProperties(eventsRDD: RDD[Event]): RDD[(String, PropertyMap)] = {
    eventsRDD
      .map( e => (e.entityId, EventOp(e) ))
      .aggregateByKey[EventOp](EventOp())(
        // within same partition
        seqOp = { case (u, v) => u ++ v },
        // across partition
        combOp = { case (accu, u) => accu ++ u }
      )
      .mapValues(_.toPropertyMap)
      .filter{ case (k, v) => v.isDefined }
      .map{ case (k, v) => (k, v.get) }
  }

}
