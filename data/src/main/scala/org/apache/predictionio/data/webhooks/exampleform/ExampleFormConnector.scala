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

package org.apache.predictionio.data.webhooks.exampleform

import org.apache.predictionio.data.webhooks.FormConnector
import org.apache.predictionio.data.webhooks.ConnectorException

import org.json4s.JObject


/** Example FormConnector with following types of webhook form data inputs:
  *
  * UserAction
  *
  *   "type"="userAction"
  *   "userId"="as34smg4",
  *   "event"="do_something",
  *   "context[ip]"="24.5.68.47", // optional
  *   "context[prop1]"="2.345", // optional
  *   "context[prop2]"="value1" // optional
  *   "anotherProperty1"="100",
  *   "anotherProperty2"="optional1", // optional
  *   "timestamp"="2015-01-02T00:30:12.984Z"
  *
  * UserActionItem
  *
  *   "type"="userActionItem"
  *   "userId"="as34smg4",
  *   "event"="do_something_on",
  *   "itemId"="kfjd312bc",
  *   "context[ip]"="1.23.4.56",
  *   "context[prop1]"="2.345",
  *   "context[prop2]"="value1",
  *   "anotherPropertyA"="4.567", // optional
  *   "anotherPropertyB"="false", // optional
  *   "timestamp"="2015-01-15T04:20:23.567Z"
  *
  */
private[predictionio] object ExampleFormConnector extends FormConnector {

  override
  def toEventJson(data: Map[String, String]): JObject = {
    val json = try {
      data.get("type") match {
        case Some("userAction") => userActionToEventJson(data)
        case Some("userActionItem") => userActionItemToEventJson(data)
        case Some(x) => throw new ConnectorException(
          s"Cannot convert unknown type ${x} to event JSON")
        case None => throw new ConnectorException(
          s"The field 'type' is required.")
      }
    } catch {
      case e: ConnectorException => throw e
      case e: Exception => throw new ConnectorException(
        s"Cannot convert ${data} to event JSON. ${e.getMessage()}", e)
    }
    json
  }

  def userActionToEventJson(data: Map[String, String]): JObject = {
    import org.json4s.JsonDSL._

    // two level optional data
    val context = if (data.exists(_._1.startsWith("context["))) {
      Some(
        ("ip" -> data.get("context[ip]")) ~
        ("prop1" -> data.get("context[prop1]").map(_.toDouble)) ~
        ("prop2" -> data.get("context[prop2]"))
      )
    } else {
      None
    }

    val json =
      ("event" -> data("event")) ~
      ("entityType" -> "user") ~
      ("entityId" -> data("userId")) ~
      ("eventTime" -> data("timestamp")) ~
      ("properties" -> (
        ("context" -> context) ~
        ("anotherProperty1" -> data("anotherProperty1").toInt) ~
        ("anotherProperty2" -> data.get("anotherProperty2"))
      ))
    json
  }


  def userActionItemToEventJson(data: Map[String, String]): JObject = {
    import org.json4s.JsonDSL._

    val json =
      ("event" -> data("event")) ~
      ("entityType" -> "user") ~
      ("entityId" -> data("userId")) ~
      ("targetEntityType" -> "item") ~
      ("targetEntityId" -> data("itemId")) ~
      ("eventTime" -> data("timestamp")) ~
      ("properties" -> (
        ("context" -> (
          ("ip" -> data("context[ip]")) ~
          ("prop1" -> data("context[prop1]").toDouble) ~
          ("prop2" -> data("context[prop2]"))
        )) ~
        ("anotherPropertyA" -> data.get("anotherPropertyA").map(_.toDouble)) ~
        ("anotherPropertyB" -> data.get("anotherPropertyB").map(_.toBoolean))
      ))
    json
  }

}
