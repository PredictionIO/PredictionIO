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

package org.apache.predictionio.data.webhooks.examplejson

import org.apache.predictionio.data.webhooks.ConnectorTestUtil

import org.specs2.mutable._

/** Test the ExampleJsonConnector */
class ExampleJsonConnectorSpec extends Specification with ConnectorTestUtil {

  "ExampleJsonConnector" should {

    "convert userAction to Event JSON" in {
      // webhooks input
      val userAction = """
        {
          "type": "userAction"
          "userId": "as34smg4",
          "event": "do_something",
          "context": {
            "ip": "24.5.68.47",
            "prop1": 2.345
            "prop2": "value1"
          },
          "anotherProperty1": 100,
          "anotherProperty2": "optional1",
          "timestamp": "2015-01-02T00:30:12.984Z"
        }
      """

      // expected converted Event JSON
      val expected = """
        {
          "event": "do_something",
          "entityType": "user",
          "entityId": "as34smg4",
          "properties": {
            "context": {
              "ip": "24.5.68.47",
              "prop1": 2.345
              "prop2": "value1"
            },
            "anotherProperty1": 100,
            "anotherProperty2": "optional1"
          }
          "eventTime": "2015-01-02T00:30:12.984Z"
        }
      """

      check(ExampleJsonConnector, userAction, expected)
    }

    "convert userAction without optional field to Event JSON" in {
      // webhooks input
      val userAction = """
        {
          "type": "userAction"
          "userId": "as34smg4",
          "event": "do_something",
          "anotherProperty1": 100,
          "timestamp": "2015-01-02T00:30:12.984Z"
        }
      """

      // expected converted Event JSON
      val expected = """
        {
          "event": "do_something",
          "entityType": "user",
          "entityId": "as34smg4",
          "properties": {
            "anotherProperty1": 100,
          }
          "eventTime": "2015-01-02T00:30:12.984Z"
        }
      """

      check(ExampleJsonConnector, userAction, expected)
    }

    "convert userActionItem to Event JSON" in {
      // webhooks input
      val userActionItem = """
        {
          "type": "userActionItem"
          "userId": "as34smg4",
          "event": "do_something_on",
          "itemId": "kfjd312bc",
          "context": {
            "ip": "1.23.4.56",
            "prop1": 2.345
            "prop2": "value1"
          },
          "anotherPropertyA": 4.567
          "anotherPropertyB": false
          "timestamp": "2015-01-15T04:20:23.567Z"
      }
      """

      // expected converted Event JSON
      val expected = """
        {
          "event": "do_something_on",
          "entityType": "user",
          "entityId": "as34smg4",
          "targetEntityType": "item",
          "targetEntityId": "kfjd312bc"
          "properties": {
            "context": {
              "ip": "1.23.4.56",
              "prop1": 2.345
              "prop2": "value1"
            },
            "anotherPropertyA": 4.567
            "anotherPropertyB": false
          }
          "eventTime": "2015-01-15T04:20:23.567Z"
        }
      """

      check(ExampleJsonConnector, userActionItem, expected)
    }

    "convert userActionItem without optional fields to Event JSON" in {
      // webhooks input
      val userActionItem = """
        {
          "type": "userActionItem"
          "userId": "as34smg4",
          "event": "do_something_on",
          "itemId": "kfjd312bc",
          "context": {
            "ip": "1.23.4.56",
            "prop1": 2.345
            "prop2": "value1"
          }
          "timestamp": "2015-01-15T04:20:23.567Z"
      }
      """

      // expected converted Event JSON
      val expected = """
        {
          "event": "do_something_on",
          "entityType": "user",
          "entityId": "as34smg4",
          "targetEntityType": "item",
          "targetEntityId": "kfjd312bc"
          "properties": {
            "context": {
              "ip": "1.23.4.56",
              "prop1": 2.345
              "prop2": "value1"
            }
          }
          "eventTime": "2015-01-15T04:20:23.567Z"
        }
      """

      check(ExampleJsonConnector, userActionItem, expected)
    }

  }

}
