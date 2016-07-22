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
import org.joda.time.DateTimeZone

trait TestEvents {

  val u1BaseTime = new DateTime(654321)
  val u2BaseTime = new DateTime(6543210)
  val u3BaseTime = new DateTime(6543410)

  // u1 events
  val u1e1 = Event(
    event = "$set",
    entityType = "user",
    entityId = "u1",
    properties = DataMap(
      """{
        "a" : 1,
        "b" : "value2",
        "d" : [1, 2, 3],
      }"""),
    eventTime = u1BaseTime
  )

  val u1e2 = u1e1.copy(
    event = "$set",
    properties = DataMap("""{"a" : 2}"""),
    eventTime = u1BaseTime.plusDays(1)
  )

  val u1e3 = u1e1.copy(
    event = "$set",
    properties = DataMap("""{"b" : "value4"}"""),
    eventTime = u1BaseTime.plusDays(2)
  )

  val u1e4 = u1e1.copy(
    event = "$unset",
    properties = DataMap("""{"b" : null}"""),
    eventTime = u1BaseTime.plusDays(3)
  )

  val u1e5 = u1e1.copy(
    event = "$set",
    properties = DataMap("""{"e" : "new"}"""),
    eventTime = u1BaseTime.plusDays(4)
  )

  val u1LastTime = u1BaseTime.plusDays(4)
  val u1 = """{"a": 2, "d": [1, 2, 3], "e": "new"}"""

  // delete event for u1
  val u1ed = u1e1.copy(
    event = "$delete",
    properties = DataMap(),
    eventTime = u1BaseTime.plusDays(5)
  )

  // u2 events
  val u2e1 = Event(
    event = "$set",
    entityType = "user",
    entityId = "u2",
    properties = DataMap(
      """{
        "a" : 21,
        "b" : "value12",
        "d" : [7, 5, 6],
      }"""),
    eventTime = u2BaseTime
  )

  val u2e2 = u2e1.copy(
    event = "$unset",
    properties = DataMap("""{"a" : null}"""),
    eventTime = u2BaseTime.plusDays(1)
  )

  val u2e3 = u2e1.copy(
    event = "$set",
    properties = DataMap("""{"b" : "value9", "g": "new11"}"""),
    eventTime = u2BaseTime.plusDays(2)
  )

  val u2LastTime = u2BaseTime.plusDays(2)
  val u2 = """{"b": "value9", "d": [7, 5, 6], "g": "new11"}"""

  // u3 events
  val u3e1 = Event(
    event = "$set",
    entityType = "user",
    entityId = "u3",
    properties = DataMap(
      """{
        "a" : 22,
        "b" : "value13",
        "d" : [5, 6, 1],
      }"""),
    eventTime = u3BaseTime
  )

  val u3e2 = u3e1.copy(
    event = "$unset",
    properties = DataMap("""{"a" : null}"""),
    eventTime = u3BaseTime.plusDays(1)
  )

  val u3e3 = u3e1.copy(
    event = "$set",
    properties = DataMap("""{"b" : "value10", "f": "new12", "d" : [1, 3, 2]}"""),
    eventTime = u3BaseTime.plusDays(2)
  )

  val u3LastTime = u3BaseTime.plusDays(2)
  val u3 = """{"b": "value10", "d": [1, 3, 2], "f": "new12"}"""

  // some random events
  val r1 = Event(
    event = "my_event",
    entityType = "my_entity_type",
    entityId = "my_entity_id",
    targetEntityType = Some("my_target_entity_type"),
    targetEntityId = Some("my_target_entity_id"),
    properties = DataMap(
      """{
        "prop1" : 1,
        "prop2" : "value2",
        "prop3" : [1, 2, 3],
        "prop4" : true,
        "prop5" : ["a", "b", "c"],
        "prop6" : 4.56
      }"""
    ),
    eventTime = DateTime.now,
    prId = Some("my_prid")
  )
  val r2 = Event(
    event = "my_event2",
    entityType = "my_entity_type2",
    entityId = "my_entity_id2"
  )
  val r3 = Event(
    event = "my_event3",
    entityType = "my_entity_type",
    entityId = "my_entity_id",
    targetEntityType = Some("my_target_entity_type"),
    targetEntityId = Some("my_target_entity_id"),
    properties = DataMap(
      """{
        "propA" : 1.2345,
        "propB" : "valueB",
      }"""
    ),
    prId = Some("my_prid")
  )
  val r4 = Event(
    event = "my_event4",
    entityType = "my_entity_type4",
    entityId = "my_entity_id4",
    targetEntityType = Some("my_target_entity_type4"),
    targetEntityId = Some("my_target_entity_id4"),
    properties = DataMap(
      """{
        "prop1" : 1,
        "prop2" : "value2",
        "prop3" : [1, 2, 3],
        "prop4" : true,
        "prop5" : ["a", "b", "c"],
        "prop6" : 4.56
      }"""),
    eventTime = DateTime.now
  )
  val r5 = Event(
    event = "my_event5",
    entityType = "my_entity_type5",
    entityId = "my_entity_id5",
    targetEntityType = Some("my_target_entity_type5"),
    targetEntityId = Some("my_target_entity_id5"),
    properties = DataMap(
      """{
        "prop1" : 1,
        "prop2" : "value2",
        "prop3" : [1, 2, 3],
        "prop4" : true,
        "prop5" : ["a", "b", "c"],
        "prop6" : 4.56
      }"""
    ),
    eventTime = DateTime.now
  )
  val r6 = Event(
    event = "my_event6",
    entityType = "my_entity_type6",
    entityId = "my_entity_id6",
    targetEntityType = Some("my_target_entity_type6"),
    targetEntityId = Some("my_target_entity_id6"),
    properties = DataMap(
      """{
        "prop1" : 6,
        "prop2" : "value2",
        "prop3" : [6, 7, 8],
        "prop4" : true,
        "prop5" : ["a", "b", "c"],
        "prop6" : 4.56
      }"""
    ),
    eventTime = DateTime.now
  )

  // timezone
  val tz1 = Event(
    event = "my_event",
    entityType = "my_entity_type",
    entityId = "my_entity_id0",
    targetEntityType = Some("my_target_entity_type"),
    targetEntityId = Some("my_target_entity_id"),
    properties = DataMap(
      """{
        "prop1" : 1,
        "prop2" : "value2",
        "prop3" : [1, 2, 3],
        "prop4" : true,
        "prop5" : ["a", "b", "c"],
        "prop6" : 4.56
      }"""
    ),
    eventTime = new DateTime(12345678, DateTimeZone.forID("-08:00")),
    prId = Some("my_prid")
  )

  val tz2 = Event(
    event = "my_event",
    entityType = "my_entity_type",
    entityId = "my_entity_id1",
    eventTime = new DateTime(12345678, DateTimeZone.forID("+02:00")),
    prId = Some("my_prid")
  )

  val tz3 = Event(
    event = "my_event",
    entityType = "my_entity_type",
    entityId = "my_entity_id2",
    eventTime = new DateTime(12345678, DateTimeZone.forID("+08:00")),
    prId = Some("my_prid")
  )

}
