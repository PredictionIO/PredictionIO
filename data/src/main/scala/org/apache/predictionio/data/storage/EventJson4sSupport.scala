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

import org.apache.predictionio.annotation.DeveloperApi
import org.apache.predictionio.data.{Utils => DataUtils}
import org.joda.time.DateTime
import org.json4s._
import scala.util.{Try, Success, Failure}

/** :: DeveloperApi ::
  * Support library for dealing with [[Event]] and JSON4S
  *
  * @group Event Data
  */
@DeveloperApi
object EventJson4sSupport {
  /** This is set to org.json4s.DefaultFormats. Do not use JSON4S to serialize
    * or deserialize Joda-Time DateTime because it has some issues with timezone
    * (as of version 3.2.10)
    */
  implicit val formats = DefaultFormats

  /** :: DeveloperApi ::
    * Convert JSON from Event Server to [[Event]]
    *
    * @return deserialization routine used by [[APISerializer]]
    */
  @DeveloperApi
  def readJson: PartialFunction[JValue, Event] = {
    case JObject(x) => {
      val fields = new DataMap(x.toMap)
      // use get() if required in json
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
      // val tags = fields.getOpt[Seq[String]]("tags").getOrElse(List())

        val prId = fields.getOpt[String]("prId")

        // don't allow user set creationTime from API for now.
        val creationTime = currentTime
      // val creationTime = fields.getOpt[String]("creationTime")
      //   .map{ s =>
      //     try {
      //       DataUtils.stringToDateTime(s)
      //     } catch {
      //       case _: Exception =>
      //         throw new MappingException(s"Fail to extract creationTime ${s}")
      //     }
      //   }.getOrElse(currentTime)


        val newEvent = Event(
          event = event,
          entityType = entityType,
          entityId = entityId,
          targetEntityType = targetEntityType,
          targetEntityId = targetEntityId,
          properties = DataMap(properties),
          eventTime = eventTime,
          prId = prId,
          creationTime = creationTime
        )
        EventValidation.validate(newEvent)
        newEvent
      } catch {
        case e: Exception => throw new MappingException(e.toString, e)
      }
    }
  }

  /** :: DeveloperApi ::
    * Convert [[Event]] to JSON for use by the Event Server
    *
    * @return serialization routine used by [[APISerializer]]
    */
  @DeveloperApi
  def writeJson: PartialFunction[Any, JValue] = {
    case d: Event => {
      JObject(
        JField("eventId",
          d.eventId.map( eid => JString(eid)).getOrElse(JNothing)) ::
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
        // JField("tags", JArray(d.tags.toList.map(JString(_)))) ::
        // disable tags from API for now
        JField("prId",
          d.prId.map(JString(_)).getOrElse(JNothing)) ::
        // don't show creationTime for now
        JField("creationTime",
          JString(DataUtils.dateTimeToString(d.creationTime))) ::
        Nil)
    }
  }

  /** :: DeveloperApi ::
    * Convert JSON4S JValue to [[Event]]
    *
    * @return deserialization routine used by [[DBSerializer]]
    */
  @DeveloperApi
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
      val prId = (jv \ "prId").extract[Option[String]]
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
        prId = prId,
        creationTime = creationTime)
    }
  }

  /** :: DeveloperApi ::
    * Convert [[Event]] to JSON4S JValue
    *
    * @return serialization routine used by [[DBSerializer]]
    */
  @DeveloperApi
  def serializeToJValue: PartialFunction[Any, JValue] = {
    case d: Event => {
      JObject(
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
        JField("prId",
          d.prId.map(JString(_)).getOrElse(JNothing)) ::
        JField("creationTime",
          JString(DataUtils.dateTimeToString(d.creationTime))) ::
        Nil)
    }
  }

  /** :: DeveloperApi ::
    * Custom JSON4S serializer for [[Event]] intended to be used by database
    * access, or anywhere that demands serdes of [[Event]] to/from JSON4S JValue
    */
  @DeveloperApi
  class DBSerializer extends CustomSerializer[Event](format => (
    deserializeFromJValue, serializeToJValue))

  /** :: DeveloperApi ::
    * Custom JSON4S serializer for [[Event]] intended to be used by the Event
    * Server, or anywhere that demands serdes of [[Event]] to/from JSON
    */
  @DeveloperApi
  class APISerializer extends CustomSerializer[Event](format => (
    readJson, writeJson))
}


@DeveloperApi
object BatchEventsJson4sSupport {
  implicit val formats = DefaultFormats

  @DeveloperApi
  def readJson: PartialFunction[JValue, Seq[Try[Event]]] = {
    case JArray(events) => {
      events.map { event =>
        try {
          Success(EventJson4sSupport.readJson(event))
        } catch {
          case e: Exception => Failure(e)
        }
      }
    }
  }

  @DeveloperApi
  class APISerializer extends CustomSerializer[Seq[Try[Event]]](format => (readJson, Map.empty))
}
