/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.predictionio.core

import grizzled.slf4j.Logger
import org.apache.predictionio.annotation.DeveloperApi
import org.apache.predictionio.data.storage.{DataMap, Event,Storage}
import org.apache.predictionio.data.store.{Common, LEventStore, PEventStore}
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.joda.time.DateTime
import org.json4s._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

/** :: DeveloperApi ::
  * Base class of cleaned data source.
  *
  * A cleaned data source consists tools for cleaning events that happened earlier that
  * specified duration in seconds from train moment. Also it can remove duplicates and compress
  * properties(flat set/unset events to one)
  *
  */
@DeveloperApi
trait SelfCleaningDataSource {

  implicit object DateTimeOrdering extends Ordering[DateTime] {
    def compare(d1: DateTime, d2: DateTime): Int = d2.compareTo(d1)
  }


  @transient lazy private val pEventsDb = Storage.getPEvents()
  @transient lazy private val lEventsDb = Storage.getLEvents()

  /** :: DeveloperApi ::
    * Current App name which events will be cleaned.
    *
    * @return App name
    */
  @DeveloperApi
  def appName: String

  /** :: DeveloperApi ::
    * Param list that used for cleanup.
    *
    * @return current event windows that will be used to clean up events.
    */
  @DeveloperApi
  def eventWindow: Option[EventWindow] = None

  @transient lazy val logger = Logger[this.type]

  /** :: DeveloperApi ::
    *
    * Returns RDD of events happened after duration in event window params.
    *
    * @return RDD[Event] most recent PEvents.
    */
  @DeveloperApi
  def getCleanedPEvents(pEvents: RDD[Event]): RDD[Event] = {
    eventWindow
      .flatMap(_.duration)
      .map { duration =>
        val fd = Duration(duration)
        pEvents.filter(e =>
          e.eventTime.isAfter(DateTime.now().minus(fd.toMillis)) || isSetEvent(e)
        )
      }.getOrElse(pEvents)
  }

  /** :: DeveloperApi ::
    *
    * Returns Iterator of events happened after duration in event window params.
    *
    * @return Iterator[Event] most recent LEvents.
    */
  @DeveloperApi
  def getCleanedLEvents(lEvents: Iterable[Event]): Iterable[Event] = {
    eventWindow
      .flatMap(_.duration)
      .map { duration =>
        val fd = Duration(duration)
        lEvents.filter(e =>
          e.eventTime.isAfter(DateTime.now().minus(fd.toMillis)) || isSetEvent(e)
        )
      }.getOrElse(lEvents)
  }

  def compressPProperties(sc: SparkContext, rdd: RDD[Event]): RDD[Event] = {
    rdd.filter(isSetEvent)
      .groupBy(_.entityType)
      .flatMap { pair =>
        val (_, ls) = pair
        ls.groupBy(_.entityId).map { anotherpair =>
          val (_, anotherls) = anotherpair
          compress(anotherls)
        }
      } ++ rdd.filter(!isSetEvent(_))
  }

  def compressLProperties(events: Iterable[Event]): Iterable[Event] = {
    events.filter(isSetEvent)
      .groupBy(_.entityType)
      .map { pair =>
        val (_, ls) = pair
        compress(ls)
      } ++ events.filter(!isSetEvent(_))
  }

  def removePDuplicates(sc: SparkContext, rdd: RDD[Event]): RDD[Event] = {
    val now = DateTime.now()
    rdd.sortBy(_.eventTime, true).map(x =>
      (recreateEvent(x, None, now), (x.eventId, x.eventTime)))
      .groupByKey
      .map{case (x, y) => recreateEvent(x, y.head._1, y.head._2)}

  }

  def recreateEvent(x: Event, eventId: Option[String], creationTime: DateTime): Event = {
    Event(eventId = eventId, event = x.event, entityType = x.entityType,
      entityId = x.entityId, targetEntityType = x.targetEntityType,
      targetEntityId = x.targetEntityId, properties = x.properties,
      eventTime = creationTime, tags = x.tags, prId= x.prId,
      creationTime = creationTime)
  }

  def removeLDuplicates(ls: Iterable[Event]): Iterable[Event] = {
    val now = DateTime.now()
    ls.toList.reverse.map(x =>
      (recreateEvent(x, None, now), (x.eventId, x.eventTime)))
      .groupBy(_._1).mapValues( _.map( _._2 ) )
      .map(x => recreateEvent(x._1, x._2.head._1, x._2.head._2))

  }

  /** :: DeveloperApi ::
    *
    * Filters most recent, compress properties and removes duplicates of PEvents
    *
    * @return RDD[Event] most recent PEvents
    */
  @DeveloperApi
  def cleanPersistedPEvents(sc: SparkContext): Unit ={
    eventWindow match {
      case Some(ew) =>
        val result = cleanPEvents(sc)
        val originalEvents = PEventStore.find(appName)(sc)
        val newEvents = result subtract originalEvents
        val eventsToRemove = (originalEvents subtract result).map { e =>
          e.eventId.getOrElse("")
        }

        wipePEvents(newEvents, eventsToRemove, sc)
      case None =>
    }
  }

  /** Replace events in Event Store */
  def wipePEvents(
    newEvents: RDD[Event],
    eventsToRemove: RDD[String],
    sc: SparkContext
  ): Unit = {
    val (appId, channelId) = Common.appNameToId(appName, None)
    pEventsDb.write(newEvents.map(x => recreateEvent(x, None, x.eventTime)), appId)(sc)

    removePEvents(eventsToRemove, appId, sc)
  }

  def removeEvents(eventsToRemove: Set[String], appId: Int) {
    val listOfFuture: List[Future[Boolean]] = eventsToRemove
      .filter(x =>  x != "").toList.map { eventId =>
      lEventsDb.futureDelete(eventId, appId)
    }

    val futureOfList: Future[List[Boolean]] = Future.sequence(listOfFuture)
    Await.result(futureOfList, scala.concurrent.duration.Duration(60, "minutes"))
  }

  def removePEvents(eventsToRemove: RDD[String], appId: Int, sc: SparkContext) {
    pEventsDb.delete(eventsToRemove.filter(x =>  x != ""), appId, None)(sc)
  }


  /** Replace events in Event Store
    *
    * @param newEvents new events
    * @param eventsToRemove event ids to remove
    */
  def wipe(
    newEvents: Set[Event],
    eventsToRemove: Set[String]
  ): Unit = {
    val (appId, channelId) = Common.appNameToId(appName, None)

    val listOfFutureNewEvents: List[Future[String]] = newEvents.toList.map { event =>
      lEventsDb.futureInsert(recreateEvent(event, None, event.eventTime), appId)
    }

    val futureOfListNewEvents: Future[List[String]] = Future.sequence(listOfFutureNewEvents)
    Await.result(futureOfListNewEvents, scala.concurrent.duration.Duration(60, "minutes"))

    removeEvents(eventsToRemove, appId)
  }


  /** :: DeveloperApi ::
    *
    * Filters most recent, compress properties of PEvents
    */
  @DeveloperApi
  def cleanPEvents(sc: SparkContext): RDD[Event] = {
    val pEvents = getCleanedPEvents(PEventStore.find(appName)(sc).sortBy(_.eventTime, false))

    val rdd = eventWindow match {
      case Some(ew) =>
        val updated =
          if (ew.compressProperties) compressPProperties(sc, pEvents) else pEvents

        val deduped = if (ew.removeDuplicates) removePDuplicates(sc, updated) else updated
        deduped
      case None =>
        pEvents
    }
    rdd
  }

  /** :: DeveloperApi ::
    *
    * Filters most recent, compress properties and removes duplicates of LEvents
    *
    * @return Iterator[Event] most recent LEvents
    */
  @DeveloperApi
  def cleanPersistedLEvents: Unit = {
    eventWindow match {
      case Some(ew) =>

        val result = cleanLEvents().toSet
        val originalEvents = LEventStore.find(appName).toSet
        val newEvents = result -- originalEvents
        val eventsToRemove = (originalEvents -- result).map { e =>
          e.eventId.getOrElse("")
        }

        wipe(newEvents, eventsToRemove)

      case None =>
    }
  }

  /** :: DeveloperApi ::
    *
    * Filters most recent, compress properties of LEvents
    */
  @DeveloperApi
  def cleanLEvents(): Iterable[Event] = {
    val lEvents = getCleanedLEvents(LEventStore.find(appName).toList.sortBy(_.eventTime).reverse)

    val events = eventWindow match {
      case Some(ew) =>
        val updated =
          if (ew.compressProperties) compressLProperties(lEvents) else lEvents
        val deduped = if (ew.removeDuplicates) removeLDuplicates(updated) else updated
        deduped
      case None =>
        lEvents
    }
    events
  }


  private def isSetEvent(e: Event): Boolean = {
    e.event == "$set" || e.event == "$unset"
  }

  private def compress(events: Iterable[Event]): Event = {
    events.find(_.event == "$set") match {

      case Some(first) =>
        events.reduce { (e1, e2) =>
          val props = e2.event match {
            case "$set" =>
              e1.properties.fields ++ e2.properties.fields
            case "$unset" =>
              e1.properties.fields
                .filterKeys(f => !e2.properties.fields.contains(f))
          }
          e1.copy(properties = DataMap(props), eventTime = e2.eventTime)
        }

      case None =>
        events.reduce { (e1, e2) =>
          e1.copy(properties =
            DataMap(e1.properties.fields ++ e2.properties.fields),
            eventTime = e2.eventTime
          )
        }
    }
  }
}

case class EventWindow(
  duration: Option[String] = None,
  removeDuplicates: Boolean = false,
  compressProperties: Boolean = false
)
