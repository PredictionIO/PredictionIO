---
title:  Contribute a Webhooks Connector
---

<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

NOTE: Please check out the [latest develop
branch](https://github.com/apache/predictionio).

Event server can collect data from other third-party sites or software through their webhooks services (for example, SegmentIO, MailChimp). To support that, a *Webhooks Connector* for the third-party data is needed to be integrated into Event Server. The job of the *Webhooks Connector* is as simply as converting the third-party data into Event JSON. You can find an example below.

Currently we support two types of connectors: `JsonConnector` and `FormConnector`, which is responsible for accepting *JSON* data and *Form-submission* data, respectively.

**JsonConnector**:

```scala
package org.apache.predictionio.data.webhooks

/** Connector for Webhooks connection */
private[predictionio] trait JsonConnector {

  /** Convert from original JObject to Event JObject
    * @param data original JObject recevived through webhooks
    * @return Event JObject
   */
  def toEventJson(data: JObject): JObject

}

```

The EventServer URL path to collect webhooks JSON data:

```
http://<EVENT SERVER URL>/webhooks/<CONNECTOR_NAME>.json?accessKey=<YOUR_ACCESS_KEY>&channel=<CHANNEL_NAME>
```

Note that you may collect Webhooks data into default channel (without the `channel` parameter in the URL) but it's highly recommended to create dedicated [Channel](/datacollection/channel/) to collect specific Webhooks data (e.g. create one channel "segmentio" for SegmentIO and another channel "mailchimp" for Mailchimp data) because it allows you to manage and query data more easily, and the Webhooks data won't be mixed with your other normal app data.


**FormConnector**:

```scala
package org.apache.predictionio.data.webhooks

/** Connector for Webhooks connection with Form submission data format
  */
private[predictionio] trait FormConnector {

  /** Convert from original Form submission data to Event JObject
    * @param data Map of key-value pairs in String type received through webhooks
    * @return Event JObject
   */
  def toEventJson(data: Map[String, String]): JObject

}

```

The EventServer URL path to collect webhooks form-subimssion data (no .json):

```
http://<EVENT SERVER URL>/webhooks/<CONNECTOR_NAME>?accessKey=<YOUR_ACCESS_KEY>&channel=<CHANNEL_NAME>
```

Note that you may collect Webhooks data into default channel (without the `channel` parameter in the URL) but it's highly recommended to create dedicated [Channel](/datacollection/channel/) to collect specific Webhooks data (e.g. create one channel "segmentio" for SegmentIO and another channel "mailchimp" for Mailchimp data) because it allows you to manage and query data more easily, and the Webhooks data won't be mixed with your other normal app data.


# Example

For example, let's say there is a third-party website (say, it is named "ExampleJson") which can send the following JSON data through its webhooks service and we would like to collect it into Event Store.

**UserActionItem**:

```json
{
  "type": "userActionItem",
  "userId": "as34smg4",
  "event": "do_something_on",
  "itemId": "kfjd312bc",
  "context": {
    "ip": "1.23.4.56",
    "prop1": 2.345,
    "prop2": "value1"
  },
  "anotherPropertyA": 4.567,
  "anotherPropertyB": false,
  "timestamp": "2015-01-15T04:20:23.567Z"
}
```


## 1. Implement Webhooks Connector

Because the data sent by this third-party "ExampleJson" site is in JSON format, we implement an object `ExampleJsonConnector` which extends `JsonConnector`:


```scala
private[predictionio] object ExampleJsonConnector extends JsonConnector {

  implicit val json4sFormats: Formats = DefaultFormats

  override def toEventJson(data: JObject): JObject = {
    val common = try {
      data.extract[Common]
    } catch {
      case e: Exception => throw new ConnectorException(
        s"Cannot extract Common field from ${data}. ${e.getMessage()}", e)
    }

    val json = try {
      common.`type` match {
        case "userActionItem" =>
          toEventJson(common = common, userActionItem = data.extract[UserActionItem])
        case x: String =>
          throw new ConnectorException(
            s"Cannot convert unknown type '${x}' to Event JSON.")
      }
    } catch {
      case e: ConnectorException => throw e
      case e: Exception => throw new ConnectorException(
        s"Cannot convert ${data} to eventJson. ${e.getMessage()}", e)
    }

    json
  }

  // Convert the UserActionItem JSON to Event JSON
  def toEventJson(common: Common, userActionItem: UserActionItem): JObject = {
    import org.json4s.JsonDSL._

    // map to EventAPI JSON
    val json =
      ("event" -> userActionItem.event) ~
      ("entityType" -> "user") ~
      ("entityId" -> userActionItem.userId) ~
      ("targetEntityType" -> "item") ~
      ("targetEntityId" -> userActionItem.itemId) ~
      ("eventTime" -> userActionItem.timestamp) ~
      ("properties" -> (
        ("context" -> userActionItem.context) ~
        ("anotherPropertyA" -> userActionItem.anotherPropertyA) ~
        ("anotherPropertyB" -> userActionItem.anotherPropertyB)
      ))
    json
  }

  // Common required fields
  case class Common(
    `type`: String
  )

  // UserActionItem fields
  case class UserActionItem (
    userId: String,
    event: String,
    itemId: String,
    context: JObject,
    anotherPropertyA: Option[Double],
    anotherPropertyB: Option[Boolean],
    timestamp: String
  )

}
```

You can find the complete example in [the GitHub
repo](https://github.com/apache/predictionio/blob/develop/data/src/main/scala/org/apache/predictionio/data/webhooks/examplejson/ExampleJsonConnector.scala)
and how to write [tests for the
connector](https://github.com/apache/predictionio/blob/develop/data/src/test/scala/org/apache/predictionio/data/webhooks/examplejson/ExampleJsonConnectorSpec.scala).


Please put the connector code in a separate directory for each site. For example, code for segmentio connector should be in

```
data/src/main/scala/org.apache.predictionio/data/webhooks/segmentio/
```

and tests should be in

```
data/src/test/scala/org.apache.predictionio/data/webhooks/segmentio/
```

**For form-submission data**, you can find the complete example [the GitHub
repo](https://github.com/apache/predictionio/blob/develop/data/src/main/scala/org/apache/predictionio/data/webhooks/exampleform/ExampleFormConnector.scala)
and how to write [tests for the
connector](https://github.com/apache/predictionio/blob/develop/data/src/test/scala/org/apache/predictionio/data/webhooks/exampleform/ExampleFormConnectorSpec.scala).


## 2. Integrate the Connector into Event Server

Once we have the connector implemented, we can add this to the EventServer so we can collect real-time data.

Add the connector to [`WebhooksConnectors` object](
https://github.com/apache/predictionio/blob/develop/data/src/main/scala/org/apache/predictionio/data/api/WebhooksConnectors.scala):

```scala

import org.apache.predictionio.data.webhooks.examplejson.ExampleJsonConnector // ADDED

private[predictionio] object WebhooksConnectors {

  // Map of Connector Name to Connector
  val json: Map[String, JsonConnector] = Map(
    "segmentio" -> SegmentIOConnector,
    "examplejson" -> ExampleJsonConnector // ADDED
  )

  // Map of Connector Name to Connector
  val form: Map[String, FormConnector] = Map(
    "mailchimp" -> MailChimpConnector
  )

}
```

Note that the name of the connectors (e.g. "examplejson", "segmentio") will be used as the webhooks URL. In this example, the event server URL to collect data from "ExampleJson" would be:

```
http://<EVENT SERVER URL>/webhooks/examplejson.json?accessKey=<YOUR_ACCESS_KEY>&channel=<CHANNEL_NAME>
```

For `FormConnector`, the URL doesn't have `.json`. For example,

```
http://<EVENT SERVER URL>/webhooks/mailchimp?accessKey=<YOUR_ACCESS_KEY>&channel=<CHANNEL_NAME>
```

That's it. Once you re-compile Apache PredictionIO, you can send
the ExampleJson data to the following URL and the data will be stored to the App
of the corresponding Access Key.
