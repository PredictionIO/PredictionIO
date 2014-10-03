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

package io.prediction.data

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{ read, write }
import org.json4s.ext.JodaTimeSerializers
import com.github.nscala_time.time.Imports._

object SerializerTest {
  case class Event(
    val entityId: String,
    val targetEntityId: Option[String],
    val event: String,
    val properties: JObject, // TODO: don't use JObject
    val time: DateTime,
    val tags: Seq[String],
    val appId: Int,
    val predictionKey: Option[String]
  )

  case class A(a: Int, b: String, c: Option[Boolean])

  class EventSeriliazer extends CustomSerializer[Event](format => (
    {
      case jv: JValue => {
        implicit val formats = DefaultFormats.lossless
        val entityId = (jv \ "entityId").extract[String]
        val targetEntityId = (jv \ "targetEntityId").extract[Option[String]]
        val event = (jv \ "event").extract[String]
        val properties = (jv \ "properties").extract[JObject]
        val time = DateTime.now // TODO (jv \ "time").extract[]
        val tags = (jv \ "tags").extract[Seq[String]]
        val appId = (jv \ "appId").extract[Int]
        val predictionKey = (jv \ "predictionKey").extract[Option[String]]
        Event(
          entityId = entityId,
          targetEntityId = targetEntityId,
          event = event,
          properties = properties,
          time = time,
          tags = tags,
          appId = appId,
          predictionKey = predictionKey)
      }
      /*case JObject(
        JField("a", JInt(a)) ::
        JField("b", JString(b)) ::
        Nil) => new A(a.intValue,b)*/
    },
    {
      case d: Event =>
        JObject(
          JField("entityId", JString(d.entityId)) ::
          JField("targetEntityId",
            d.targetEntityId.map(JString(_)).getOrElse(JNothing)) ::
          JField("event", JString(d.event)) ::
          JField("properties", d.properties) ::
          JField("time", JString("TODO")) ::
          JField("tags", JArray(d.tags.toList.map(JString(_)))) ::
          JField("appId", JInt(d.appId)) ::
          JField("predictionKey",
            d.predictionKey.map(JString(_)).getOrElse(JNothing)) ::
          Nil)
    }
  ))
  def main(args: Array[String]) {
    test()
  }

  def test() = {
    implicit val formats = DefaultFormats.lossless +
      new EventSeriliazer

    val j = parse("""{
      "a": 1, "b": "some"} """)

    /*val w = read[A]("""{"b": 1,
      "a": 1 } """)*/
    val w = read[Event]("""{
      "event" : "my_event",
      "entityId" : "my_entity_id",
      "targetEntityId" : "my_target_entity_id",
      "properties" : {
        "prop1" : "value1",
        "prop2" : "value2"
      }
      "time" : "2004-12-13T21:39:45.618-08:00",
      "tags" : ["tag1", "tag2"],
      "appId" : 4,
      "predictionKey" : "my_prediction_key"
    }""")
    println(w)
    println(write(w))
    println("test")

  }
}


object Test {
  case class TestEvent(
    val d: Int,
    val e: Map[String, Any]
  )
  def main(args: Array[String]) {
    implicit val formats = DefaultFormats // Brings in default date formats etc.

    val json = parse("""
    {"d": 1, "e" : {"a": "some_string", "b": true, "c" : 4}}""")
    println(json)
    val d = json.extract[TestEvent]
    println(d)
  }
}
