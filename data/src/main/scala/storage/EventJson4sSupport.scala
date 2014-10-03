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

import io.prediction.data.{ Utils => DataUtils }

import org.json4s._
import org.json4s.native.Serialization.{ read, write }

import org.joda.time.DateTime

object EventJson4sSupport {

  // NOTE: don't use json4s to serialize/deserialize joda DateTime
  // because it has some issue with timezone (as of version 3.2.10)
  implicit val formats = DefaultFormats

  def readJson: PartialFunction[JValue, Event] = {
    case JObject(x) => {
      val fields = new DataMap(x.toMap)
      // use get() if requried in json
      // use getOpt() if not required in json
      try {
        val event = fields.get[String]("event")
        val entityType = fields.get[String]("entityType")
        val entityId = fields.get[String]("entityId")
        val targetEntityType = fields.getOpt[String]("targetEntityType")
        val targetEntityId = fields.getOpt[String]("targetEntityId")
        val properties = fields.getOrElse[Map[String, JValue]](
          "properties", Map())
        // default currentTime expressed as UTC timezone
        lazy val currentTime = DateTime.now(EventValidation.defaultTimeZone)
        val eventTime = fields.getOpt[String]("eventTime")
          .map{ s =>
            try {
              DataUtils.stringToDateTime(s)
            } catch {
              case _: Exception =>
                throw new MappingException(s"Fail to extract eventTime ${s}")
            }
          }.getOrElse(currentTime)

        // disable tags from API for now.
        val tags = List()
      //val tags = fields.getOpt[Seq[String]]("tags").getOrElse(List())

        val appId = fields.get[Int]("appId")

        // disable predictionKey from API for now
        val predictionKey = None
      //val predictionKey = fields.getOpt[String]("predictionKey")

        // don't allow user set creationTime from API for now.
        val creationTime = currentTime
      //val creationTime = fields.getOpt[String]("creationTime")
      //  .map{ s =>
      //    try {
      //      DataUtils.stringToDateTime(s)
      //    } catch {
      //      case _: Exception =>
      //        throw new MappingException(s"Fail to extract creationTime ${s}")
      //    }
      //  }.getOrElse(currentTime)


        val newEvent = Event(
          event = event,
          entityType = entityType,
          entityId = entityId,
          targetEntityType = targetEntityType,
          targetEntityId = targetEntityId,
          properties = DataMap(properties),
          eventTime = eventTime,
          appId = appId,
          predictionKey = predictionKey,
          creationTime = creationTime
        )
        EventValidation.validate(newEvent)
        newEvent
      } catch {
        case e: Exception => throw new MappingException(e.toString, e)
      }
    }
  }

  def writeJson: PartialFunction[Any, JValue] = {
    case d: Event => {
      JObject(
        JField("eventId",
          d.eventId.map( eid => JString(eid.toString)).getOrElse(JNothing)) ::
        JField("appId", JInt(d.appId)) ::
        JField("event", JString(d.event)) ::
        JField("entityType", JString(d.entityType)) ::
        JField("entityId", JString(d.entityId)) ::
        JField("targetEntityType",
          d.targetEntityType.map(JString(_)).getOrElse(JNothing)) ::
        JField("targetEntityId",
          d.targetEntityId.map(JString(_)).getOrElse(JNothing)) ::
        JField("properties", d.properties.toJObject) ::
        JField("eventTime", JString(DataUtils.dateTimeToString(d.eventTime))) ::
        // disable tags from API for now
        //JField("tags", JArray(d.tags.toList.map(JString(_)))) ::
        // disable tags from API for now
        //JField("predictionKey",
        //  d.predictionKey.map(JString(_)).getOrElse(JNothing)) ::
        // don't show creationTime for now
        //JField("creationTime",
        //  JString(DataUtils.dateTimeToString(d.creationTime))) ::
        Nil)
    }
  }

  def deserializeFromJValue: PartialFunction[JValue, Event] = {
    case jv: JValue => {
      val event = (jv \ "event").extract[String]
      val entityType = (jv \ "entityType").extract[String]
      val entityId = (jv \ "entityId").extract[String]
      val targetEntityType = (jv \ "targetEntityType").extract[Option[String]]
      val targetEntityId = (jv \ "targetEntityId").extract[Option[String]]
      val properties = (jv \ "properties").extract[JObject]
      val eventTime = DataUtils.stringToDateTime(
        (jv \ "eventTime").extract[String])
      val tags = (jv \ "tags").extract[Seq[String]]
      val appId = (jv \ "appId").extract[Int]
      val predictionKey = (jv \ "predictionKey").extract[Option[String]]
      val creationTime = DataUtils.stringToDateTime(
        (jv \ "creationTime").extract[String])
      Event(
        event = event,
        entityType = entityType,
        entityId = entityId,
        targetEntityType = targetEntityType,
        targetEntityId = targetEntityId,
        properties = DataMap(properties),
        eventTime = eventTime,
        tags = tags,
        appId = appId,
        predictionKey = predictionKey,
        creationTime = creationTime)
    }
  }

  def serializeToJValue: PartialFunction[Any, JValue] = {
    case d: Event => {
      JObject(
        JField("appId", JInt(d.appId)) ::
        JField("event", JString(d.event)) ::
        JField("entityType", JString(d.entityType)) ::
        JField("entityId", JString(d.entityId)) ::
        JField("targetEntityType",
          d.targetEntityType.map(JString(_)).getOrElse(JNothing)) ::
        JField("targetEntityId",
          d.targetEntityId.map(JString(_)).getOrElse(JNothing)) ::
        JField("properties", d.properties.toJObject) ::
        JField("eventTime", JString(DataUtils.dateTimeToString(d.eventTime))) ::
        JField("tags", JArray(d.tags.toList.map(JString(_)))) ::
        JField("predictionKey",
          d.predictionKey.map(JString(_)).getOrElse(JNothing)) ::
        JField("creationTime",
          JString(DataUtils.dateTimeToString(d.creationTime))) ::
        Nil)
    }
  }

  // for DB usage
  class DBSerializer extends CustomSerializer[Event](format => (
    deserializeFromJValue, serializeToJValue))

  // for API usage
  class APISerializer extends CustomSerializer[Event](format => (
    readJson, writeJson))
}
