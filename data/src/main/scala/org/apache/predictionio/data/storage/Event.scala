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
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

/** Each event in the Event Store can be represented by fields in this case
  * class.
  *
  * @param eventId Unique ID of this event.
  * @param event Name of this event.
  * @param entityType Type of the entity associated with this event.
  * @param entityId ID of the entity associated with this event.
  * @param targetEntityType Type of the target entity associated with this
  *                         event.
  * @param targetEntityId ID of the target entity associated with this event.
  * @param properties Properties associated with this event.
  * @param eventTime Time of the happening of this event.
  * @param tags Tags of this event.
  * @param prId PredictedResultId of this event.
  * @param creationTime Time of creation in the system of this event.
  * @group Event Data
  */
case class Event(
  val eventId: Option[String] = None,
  val event: String,
  val entityType: String,
  val entityId: String,
  val targetEntityType: Option[String] = None,
  val targetEntityId: Option[String] = None,
  val properties: DataMap = DataMap(), // default empty
  val eventTime: DateTime = DateTime.now,
  val tags: Seq[String] = Nil,
  val prId: Option[String] = None,
  val creationTime: DateTime = DateTime.now
) {
  override def toString(): String = {
    s"Event(id=$eventId,event=$event,eType=$entityType,eId=$entityId," +
    s"tType=$targetEntityType,tId=$targetEntityId,p=$properties,t=$eventTime," +
    s"tags=$tags,pKey=$prId,ct=$creationTime)"
  }
}

/** :: DeveloperApi ::
  * Utilities for validating [[Event]]s
  *
  * @group Event Data
  */
@DeveloperApi
object EventValidation {
  /** Default time zone is set to UTC */
  val defaultTimeZone = DateTimeZone.UTC

  /** Checks whether an event name contains a reserved prefix
    *
    * @param name Event name
    * @return true if event name starts with \$ or pio_, false otherwise
    */
  def isReservedPrefix(name: String): Boolean = name.startsWith("$") ||
    name.startsWith("pio_")

  /** PredictionIO reserves some single entity event names. They are currently
    * \$set, \$unset, and \$delete.
    */
  val specialEvents = Set("$set", "$unset", "$delete")

  /** Checks whether an event name is a special PredictionIO event name
    *
    * @param name Event name
    * @return true if the name is a special event, false otherwise
    */
  def isSpecialEvents(name: String): Boolean = specialEvents.contains(name)

  /** Validate an [[Event]], throwing exceptions when the candidate violates any
    * of the following:
    *
    *  - event name must not be empty
    *  - entityType must not be empty
    *  - entityId must not be empty
    *  - targetEntityType must not be Some of empty
    *  - targetEntityId must not be Some of empty
    *  - targetEntityType and targetEntityId must be both Some or None
    *  - properties must not be empty when event is \$unset
    *  - event name must be a special event if it has a reserved prefix
    *  - targetEntityType and targetEntityId must be None if the event name has
    *    a reserved prefix
    *  - entityType must be a built-in entity type if entityType has a
    *    reserved prefix
    *  - targetEntityType must be a built-in entity type if targetEntityType is
    *    Some and has a reserved prefix
    *
    * @param e Event to be validated
    */
  def validate(e: Event): Unit = {

    require(!e.event.isEmpty, "event must not be empty.")
    require(!e.entityType.isEmpty, "entityType must not be empty string.")
    require(!e.entityId.isEmpty, "entityId must not be empty string.")
    require(e.targetEntityType.map(!_.isEmpty).getOrElse(true),
      "targetEntityType must not be empty string")
    require(e.targetEntityId.map(!_.isEmpty).getOrElse(true),
      "targetEntityId must not be empty string.")
    require(!((e.targetEntityType != None) && (e.targetEntityId == None)),
      "targetEntityType and targetEntityId must be specified together.")
    require(!((e.targetEntityType == None) && (e.targetEntityId != None)),
      "targetEntityType and targetEntityId must be specified together.")
    require(!((e.event == "$unset") && e.properties.isEmpty),
      "properties cannot be empty for $unset event")
    require(!isReservedPrefix(e.event) || isSpecialEvents(e.event),
      s"${e.event} is not a supported reserved event name.")
    require(!isSpecialEvents(e.event) ||
      ((e.targetEntityType == None) && (e.targetEntityId == None)),
      s"Reserved event ${e.event} cannot have targetEntity")
    require(!isReservedPrefix(e.entityType) ||
      isBuiltinEntityTypes(e.entityType),
      s"The entityType ${e.entityType} is not allowed. " +
        s"'pio_' is a reserved name prefix.")
    require(e.targetEntityType.map{ t =>
      (!isReservedPrefix(t) || isBuiltinEntityTypes(t))}.getOrElse(true),
      s"The targetEntityType ${e.targetEntityType.get} is not allowed. " +
        s"'pio_' is a reserved name prefix.")
    validateProperties(e)
  }

  /** Defines built-in entity types. The current built-in type is pio_pr. */
  val builtinEntityTypes: Set[String] = Set("pio_pr")

  /** Defines built-in properties. This is currently empty. */
  val builtinProperties: Set[String] = Set()

  /** Checks whether an entity type is a built-in entity type */
  def isBuiltinEntityTypes(name: String): Boolean = builtinEntityTypes.contains(name)

  /** Validate event properties, throwing exceptions when the candidate violates
    * any of the following:
    *
    *  - property name must not contain a reserved prefix
    *
    * @param e Event to be validated
    */
  def validateProperties(e: Event): Unit = {
    e.properties.keySet.foreach { k =>
      require(!isReservedPrefix(k) || builtinProperties.contains(k),
        s"The property ${k} is not allowed. " +
          s"'pio_' is a reserved name prefix.")
    }
  }

}
