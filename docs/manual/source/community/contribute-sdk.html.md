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

---
title:  Contribute a SDK
---

A SDK should provide convenient methods for client applications to easily
record users' behaviors in Apache PredictionIO (incubating)'s Event Server and
also query recommendations from the ML Engines. Therefore, a SDK typically has 2
corresponding clients: `Event Client` and `Engine Client`.

The following guideline bases on the REST API provided by Apache PredictionIO
(incubating)'s  Event Client which details can be found
[here](http://docs.prediction.io/datacollection/eventapi/).

## Event Client
Because the Event Server has only 1 connection point, the `Event Client`
needs to implement this core request first. The core request has the
following rules.

- **URL**: `<base URL>/events.json?accessKey=<your access key>`
(e.g. http://localhost:7070/events.json?accessKey=1234567890)

- **Request**: `POST` + JSON data. Please refer to the [Event Creation API]
(http://docs.prediction.io/datacollection/eventapi/) for the details
on the fields of the JSON data object.

- **Response**:
    + **Success**: status code `201` with a JSON result containing
    the `eventId`.
    + **Failure**: a JSON result containing a `message` field describing
    the error.
        * Status code `401`: invalid access key.
        * Status code `400`: fail to parse the JSON request e.g. missing
        required fields like `event`, or invalid `eventTime` format.

Other convenient methods are just shortcut. They could simply build
the event's parameters and call the core request. `Event Client` should
support the following 7 shorthand operations:

- **User entities**
    + **Sets properties of a user**: with the JSON object

        ```json
        {
            "event": "$set",
            "entityType": "user",
            "entityId": <user_ID>,
            "properties": <properties>
        }
        ```

    + **Unsets some properties of a user**: with the JSON object

        ```json
        {
            "event": "$unset",
            "entityType": "user",
            "entityId": <user_ID>,
            "properties": <properties>
        }
        ```

    + **Delete a user**: with the JSON object

        ```json
        {
            "event": "$delete",
            "entityType": "user",
            "entityId": <user_ID>
        }
        ```

- **Item entities**
    + **Sets properties of an item**: with the JSON object

        ```json
        {
            "event": "$set",
            "entityType": "item",
            "entityId": <item_ID>,
            "properties": <properties>
        }
        ```

    + **Unsets some properties of an item**: with the JSON object

        ```json
        {
            "event": "$unset",
            "entityType": "item",
            "entityId": <item_ID>,
            "properties": <properties>
        }
        ```

    + **Delete an item**: with the JSON object

        ```json
        {
            "event": "$delete",
            "entityType": "item",
            "entityId": <item_ID>
        }
        ```

- **Others**
    + **Record a user's action on some item**: with the JSON object

        ```json
        {
            "event": <event_name>,
            "entityType": "user",
            "entityId": <user_ID>,
            "targetEntityType": "item",
            "targetEntityId": <item_ID>,
            "properties": <properties>
        }
        ```

Again, please refer to the [API documentation]
(http://docs.prediction.io/datacollection/eventapi/) for explanations
on the reversed events like `$set`, `$unset` or `$delete`.

INFO: The `eventTime` is optional but it is recommended that the client
application should include time in the request. Therefore, it is best
that the `Event Client` includes the time field if missing, before
sending the event to the server.


## Engine Client
`Engine Client`'s main job is to retrieve recommendation or prediction  results
from Apache PredictionIO (incubating)'s Engines. It has only a few rules on the
request and response type.

- **URL**: `<base URL>/queries.json` (e.g. http://localhost:8000/queries.json)

- **Request**: `POST` + JSON data. For example,

    ```json
    {
        "user": 1,
        "num": 4
    }
    ```

- **Response**:
    + **Success**: status code `200` with a JSON result object. For example,

        ```json
        {
            "itemScores": [
                {
                    "item": 39,
                    "score": "6.177719297832409"
                },
                {
                    "item": 79,
                    "score": "5.931687319083594"
                },
                ...
            ]
        }
        ```
    + **Failure**: status code `400` e.g. fail to parse the query.

The formats of JSON objects in both the request and response must be defined by
the Apache PredictionIO (incubating)'s Engine and are different across
applications. The above examples are taken from the Recommendation Engine
template in which the query and prediction results are defined as following.

```scala
case class Query(
  user: String,
  num: Int
) extends Serializable

case class PredictedResult(
  itemScores: Array[ItemScore]
) extends Serializable
```

## Testing Your SDK

You can set up a local host Apache PredictionIO (incubating) environment to test
your SDK. However, it is hard to set it up online to test your SDK automatically
using services like Travis CI. In that case, you should consider using these
lightweight [mock servers]
(https://github.com/minhtule/PredictionIO-Mock-Server). Please see the
instructions in the repo how to use it. It takes less than 5 minutes!

That's it! We are looking forward to see your SDK!
