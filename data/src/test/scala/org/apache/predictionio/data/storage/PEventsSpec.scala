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

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

class PEventsSpec extends Specification with TestEvents {

  System.clearProperty("spark.driver.port")
  System.clearProperty("spark.hostPort")
  val sc = new SparkContext("local[4]", "PEventAggregatorSpec test")

  val appId = 1
  val channelId = 6
  val dbName = "test_pio_storage_events_" + hashCode

  def hbLocal = Storage.getDataObject[LEvents](
    StorageTestUtils.hbaseSourceName,
    dbName
  )

  def hbPar = Storage.getDataObject[PEvents](
    StorageTestUtils.hbaseSourceName,
    dbName
  )

  def jdbcLocal = Storage.getDataObject[LEvents](
    StorageTestUtils.jdbcSourceName,
    dbName
  )

  def jdbcPar = Storage.getDataObject[PEvents](
    StorageTestUtils.jdbcSourceName,
    dbName
  )

  def stopSpark = {
    sc.stop()
  }

  def is = s2"""

  PredictionIO Storage PEvents Specification

    PEvents can be implemented by:
    - HBPEvents ${hbPEvents}
    - JDBCPEvents ${jdbcPEvents}
    - (stop Spark) ${Step(sc.stop())}

  """

  def hbPEvents = sequential ^ s2"""

    HBPEvents should
    - behave like any PEvents implementation ${events(hbLocal, hbPar)}
    - (table cleanup) ${Step(StorageTestUtils.dropHBaseNamespace(dbName))}

  """

  def jdbcPEvents = sequential ^ s2"""

    JDBCPEvents should
    - behave like any PEvents implementation ${events(jdbcLocal, jdbcPar)}
    - (table cleanup) ${Step(StorageTestUtils.dropJDBCTable(s"${dbName}_$appId"))}
    - (table cleanup) ${Step(StorageTestUtils.dropJDBCTable(s"${dbName}_${appId}_$channelId"))}

  """

  def events(localEventClient: LEvents, parEventClient: PEvents) = sequential ^ s2"""

    - (init test) ${initTest(localEventClient)}
    - (insert test events) ${insertTestEvents(localEventClient)}
    find in default ${find(parEventClient)}
    find in channel ${findChannel(parEventClient)}
    aggregate user properties in default ${aggregateUserProperties(parEventClient)}
    aggregate user properties in channel ${aggregateUserPropertiesChannel(parEventClient)}
    write to default ${write(parEventClient)}
    write to channel ${writeChannel(parEventClient)}

  """

  /* setup */

  // events from TestEvents trait
  val listOfEvents = List(u1e5, u2e2, u1e3, u1e1, u2e3, u2e1, u1e4, u1e2, r1, r2)
  val listOfEventsChannel = List(u3e1, u3e2, u3e3, r3, r4)

  def initTest(localEventClient: LEvents) = {
    localEventClient.init(appId)
    localEventClient.init(appId, Some(channelId))
  }

  def insertTestEvents(localEventClient: LEvents) = {
    listOfEvents.map( localEventClient.insert(_, appId) )
    // insert to channel
    listOfEventsChannel.map( localEventClient.insert(_, appId, Some(channelId)) )
    success
  }

  /* following are tests */

  def find(parEventClient: PEvents) = {
    val resultRDD: RDD[Event] = parEventClient.find(
      appId = appId
    )(sc)

    val results = resultRDD.collect.toList
      .map {_.copy(eventId = None)} // ignore eventId

    results must containTheSameElementsAs(listOfEvents)
  }

  def findChannel(parEventClient: PEvents) = {
    val resultRDD: RDD[Event] = parEventClient.find(
      appId = appId,
      channelId = Some(channelId)
    )(sc)

    val results = resultRDD.collect.toList
      .map {_.copy(eventId = None)} // ignore eventId

    results must containTheSameElementsAs(listOfEventsChannel)
  }

  def aggregateUserProperties(parEventClient: PEvents) = {
    val resultRDD: RDD[(String, PropertyMap)] = parEventClient.aggregateProperties(
      appId = appId,
      entityType = "user"
    )(sc)
    val result: Map[String, PropertyMap] = resultRDD.collectAsMap.toMap

    val expected = Map(
      "u1" -> PropertyMap(u1, u1BaseTime, u1LastTime),
      "u2" -> PropertyMap(u2, u2BaseTime, u2LastTime)
    )

    result must beEqualTo(expected)
  }

  def aggregateUserPropertiesChannel(parEventClient: PEvents) = {
    val resultRDD: RDD[(String, PropertyMap)] = parEventClient.aggregateProperties(
      appId = appId,
      channelId = Some(channelId),
      entityType = "user"
    )(sc)
    val result: Map[String, PropertyMap] = resultRDD.collectAsMap.toMap

    val expected = Map(
      "u3" -> PropertyMap(u3, u3BaseTime, u3LastTime)
    )

    result must beEqualTo(expected)
  }

  def write(parEventClient: PEvents) = {
    val written = List(r5, r6)
    val writtenRDD = sc.parallelize(written)
    parEventClient.write(writtenRDD, appId)(sc)

    // read back
    val resultRDD = parEventClient.find(
      appId = appId
    )(sc)

    val results = resultRDD.collect.toList
      .map { _.copy(eventId = None)} // ignore eventId

    val expected = listOfEvents ++ written

    results must containTheSameElementsAs(expected)
  }

  def writeChannel(parEventClient: PEvents) = {
    val written = List(r1, r5, r6)
    val writtenRDD = sc.parallelize(written)
    parEventClient.write(writtenRDD, appId, Some(channelId))(sc)

    // read back
    val resultRDD = parEventClient.find(
      appId = appId,
      channelId = Some(channelId)
    )(sc)

    val results = resultRDD.collect.toList
      .map { _.copy(eventId = None)} // ignore eventId

    val expected = listOfEventsChannel ++ written

    results must containTheSameElementsAs(expected)
  }

}
