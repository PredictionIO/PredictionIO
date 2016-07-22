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

import org.specs2._
import org.specs2.specification.Step

class LEventsSpec extends Specification with TestEvents {
  def is = s2"""

  PredictionIO Storage LEvents Specification

    Events can be implemented by:
    - HBLEvents ${hbEvents}
    - JDBCLEvents ${jdbcLEvents}

  """

  def hbEvents = sequential ^ s2"""

    HBLEvents should
    - behave like any LEvents implementation ${events(hbDO)}
    - (table cleanup) ${Step(StorageTestUtils.dropHBaseNamespace(dbName))}

  """

  def jdbcLEvents = sequential ^ s2"""

    JDBCLEvents should
    - behave like any LEvents implementation ${events(jdbcDO)}

  """

  val appId = 1

  def events(eventClient: LEvents) = sequential ^ s2"""

    init default ${initDefault(eventClient)}
    insert 3 test events and get back by event ID ${insertAndGetEvents(eventClient)}
    insert 3 test events with timezone and get back by event ID ${insertAndGetTimezone(eventClient)}
    insert and delete by ID ${insertAndDelete(eventClient)}
    insert test user events ${insertTestUserEvents(eventClient)}
    find user events ${findUserEvents(eventClient)}
    aggregate user properties ${aggregateUserProperties(eventClient)}
    aggregate one user properties ${aggregateOneUserProperties(eventClient)}
    aggregate non-existent user properties ${aggregateNonExistentUserProperties(eventClient)}
    init channel ${initChannel(eventClient)}
    insert 2 events to channel ${insertChannel(eventClient)}
    insert 1 event to channel and delete by ID  ${insertAndDeleteChannel(eventClient)}
    find events from channel ${findChannel(eventClient)}
    remove default ${removeDefault(eventClient)}
    remove channel ${removeChannel(eventClient)}

  """

  val dbName = "test_pio_storage_events_" + hashCode
  def hbDO = Storage.getDataObject[LEvents](
    StorageTestUtils.hbaseSourceName,
    dbName
  )

  def jdbcDO = Storage.getDataObject[LEvents](StorageTestUtils.jdbcSourceName, dbName)

  def initDefault(eventClient: LEvents) = {
    eventClient.init(appId)
  }

  def insertAndGetEvents(eventClient: LEvents) = {

    // events from TestEvents trait
    val listOfEvents = List(r1,r2,r3)

    val insertResp = listOfEvents.map { eventClient.insert(_, appId) }

    val insertedEventId: List[String] = insertResp

    val insertedEvent: List[Option[Event]] = listOfEvents.zip(insertedEventId)
      .map { case (e, id) => Some(e.copy(eventId = Some(id))) }

    val getResp = insertedEventId.map { id => eventClient.get(id, appId) }

    val getEvents = getResp

    insertedEvent must containTheSameElementsAs(getEvents)
  }

  def insertAndGetTimezone(eventClient: LEvents) = {
    val listOfEvents = List(tz1, tz2, tz3)

    val insertResp = listOfEvents.map { eventClient.insert(_, appId) }

    val insertedEventId: List[String] = insertResp

    val insertedEvent: List[Option[Event]] = listOfEvents.zip(insertedEventId)
      .map { case (e, id) => Some(e.copy(eventId = Some(id))) }

    val getResp = insertedEventId.map { id => eventClient.get(id, appId) }

    val getEvents = getResp

    insertedEvent must containTheSameElementsAs(getEvents)
  }

  def insertAndDelete(eventClient: LEvents) = {
    val eventId = eventClient.insert(r2, appId)

    val resultBefore = eventClient.get(eventId, appId)

    val expectedBefore = r2.copy(eventId = Some(eventId))

    val deleteStatus = eventClient.delete(eventId, appId)

    val resultAfter = eventClient.get(eventId, appId)

    (resultBefore must beEqualTo(Some(expectedBefore))) and
    (deleteStatus must beEqualTo(true)) and
    (resultAfter must beEqualTo(None))
  }

  def insertTestUserEvents(eventClient: LEvents) = {
    // events from TestEvents trait
    val listOfEvents = Vector(u1e5, u2e2, u1e3, u1e1, u2e3, u2e1, u1e4, u1e2)

    listOfEvents.map{ eventClient.insert(_, appId) }

    success
  }

  def findUserEvents(eventClient: LEvents) = {

    val results: List[Event] = eventClient.find(
      appId = appId,
      entityType = Some("user"))
      .toList
      .map(e => e.copy(eventId = None)) // ignore eventID

    // same events in insertTestUserEvents
    val expected = List(u1e5, u2e2, u1e3, u1e1, u2e3, u2e1, u1e4, u1e2)

    results must containTheSameElementsAs(expected)
  }

  def aggregateUserProperties(eventClient: LEvents) = {

    val result: Map[String, PropertyMap] = eventClient.aggregateProperties(
      appId = appId,
      entityType = "user")

    val expected = Map(
      "u1" -> PropertyMap(u1, u1BaseTime, u1LastTime),
      "u2" -> PropertyMap(u2, u2BaseTime, u2LastTime)
    )

    result must beEqualTo(expected)
  }

  def aggregateOneUserProperties(eventClient: LEvents) = {
    val result: Option[PropertyMap] = eventClient.aggregatePropertiesOfEntity(
      appId = appId,
      entityType = "user",
      entityId = "u1")

    val expected = Some(PropertyMap(u1, u1BaseTime, u1LastTime))

    result must beEqualTo(expected)
  }

  def aggregateNonExistentUserProperties(eventClient: LEvents) = {
    val result: Option[PropertyMap] = eventClient.aggregatePropertiesOfEntity(
      appId = appId,
      entityType = "user",
      entityId = "u999999")

    result must beEqualTo(None)
  }

  val channelId = 12

  def initChannel(eventClient: LEvents) = {
    eventClient.init(appId, Some(channelId))
  }

  def insertChannel(eventClient: LEvents) = {

    // events from TestEvents trait
    val listOfEvents = List(r4,r5)

    listOfEvents.map( eventClient.insert(_, appId, Some(channelId)) )

    success
  }

  def insertAndDeleteChannel(eventClient: LEvents) = {

    val eventId = eventClient.insert(r2, appId, Some(channelId))

    val resultBefore = eventClient.get(eventId, appId, Some(channelId))

    val expectedBefore = r2.copy(eventId = Some(eventId))

    val deleteStatus = eventClient.delete(eventId, appId, Some(channelId))

    val resultAfter = eventClient.get(eventId, appId, Some(channelId))

    (resultBefore must beEqualTo(Some(expectedBefore))) and
    (deleteStatus must beEqualTo(true)) and
    (resultAfter must beEqualTo(None))
  }

  def findChannel(eventClient: LEvents) = {

    val results: List[Event] = eventClient.find(
      appId = appId,
      channelId = Some(channelId)
    )
    .toList
    .map(e => e.copy(eventId = None)) // ignore eventId

    // same events in insertChannel
    val expected = List(r4, r5)

    results must containTheSameElementsAs(expected)
  }

  def removeDefault(eventClient: LEvents) = {
    eventClient.remove(appId)
  }

  def removeChannel(eventClient: LEvents) = {
    eventClient.remove(appId, Some(channelId))
  }
}
