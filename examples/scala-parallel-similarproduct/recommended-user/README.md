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

# Recommended User Template

This example is based on version v0.1.3 of the Similar Product Engine Template. The Similar Product Engine Template has been customized to recommend users instead of items.

The main difference from the original template is the following:

Instead of using user-to-item events to find similar items, user-to-user events are used to find similar users you may also follow, like, etc (depending on which events are used in training and how the events are used). By default, "follow" events are used.

## Overview

This engine template recommends users that are "similar" to other users.
Similarity is not defined by the user's attributes but by the user's previous actions. By default, it uses the 'follow' action such that user A and B are considered similar if most users who follows A also follows B.

This template is ideal for recommending users to other users based on their recent actions.
Use the IDs of the recently viewed users of a customer as the *Query*,
the engine will predict other users that this customer may also follow or like.

This approach works perfectly for customers who are **first-time visitors** or have not signed in.
Recommendations are made dynamically in *real-time* based on the most recent user preference you provide in the *Query*.
You can, therefore, recommend users to visitors without knowing a long history about them.

One can also use this template to build the popular feature of "people you may also follow, like, etc** quickly by provide similar users to what you have just viewed or followed.


## Highlights of the modification from original Similar Product Template

### Engine.scala

- change the Query and and Predicted Result to from "items" to "users" and "similarUsers". The "categories" field is removed:

```scala
case class Query(
  users: List[String], // MODIFED
  num: Int,
  whiteList: Option[Set[String]],
  blackList: Option[Set[String]]
) extends Serializable

case class PredictedResult(
  similarUserScores: Array[similarUserScore] // MODIFED
) extends Serializable

case class similarUserScore(
  user: String, // MODIFED
  score: Double
) extends Serializable
```

### DataSource.scala

- Since user-to-user events will be used, itemsRDD is not needed and removed.
- change from ViewEvent to FollowEvent in Training Data
- change to read "folow" events

```scala
    val followEventsRDD: RDD[FollowEvent] = eventsDb.find(
      appId = dsp.appId,
      entityType = Some("user"),
      eventNames = Some(List("follow")),
      // targetEntityType is optional field of an event.
      targetEntityType = Some(Some("user")))(sc)
      // eventsDb.find() returns RDD[Event]
      .map { event =>
        val followEvent = try {
          event.event match {
            case "follow" => FollowEvent(
              user = event.entityId,
              followedUser = event.targetEntityId.get,
              t = event.eventTime.getMillis)
            case _ => throw new Exception(s"Unexpected event $event is read.")
          }
        } catch {
          case e: Exception => {
            logger.error(s"Cannot convert $event to FollowEvent." +
              s" Exception: $e.")
            throw e
          }
        }
        followEvent
      }.cache()
```

### Preparator.scala

Change to pass the followEvents to Prepared Data.

### Algorithm.scala

Use Spark MLlib algorithm to train the productFeature vector by treating the followed user as the "product".

Modify train to use "followEvents" to create MLlibRating object and remove the code that aggregate number of views:

```scala
    val mllibRatings = data.followEvents
      .map { r =>
        // Convert user and user String IDs to Int index for MLlib
        val uindex = userStringIntMap.getOrElse(r.user, -1)
        val iindex = similarUserStringIntMap.getOrElse(r.followedUser, -1)

        if (uindex == -1)
          logger.info(s"Couldn't convert nonexistent user ID ${r.user}"
            + " to Int index.")

        if (iindex == -1)
          logger.info(s"Couldn't convert nonexistent followedUser ID ${r.followedUser}"
            + " to Int index.")

        ((uindex, iindex), 1)
      }.filter { case ((u, i), v) =>
        // keep events with valid user and user index
        (u != -1) && (i != -1)
      }
      //.reduceByKey(_ + _) // aggregate all view events of same user-item pair // NOTE: REMOVED!!
      .map { case ((u, i), v) =>
        // MLlibRating requires integer index for user and user
        MLlibRating(u, i, v)
      }
      .cache()
```

The ALSModel and predict() function is also changed accordingly.


## Usage

### Event Data Requirements

By default, this template takes the following data from Event Server as Training Data:

- User *$set* events
- User *follow* User events

### Input Query

- List of UserIDs, which are the targeted users
- N (number of users to be recommended)
- List of white-listed UserIds (optional)
- List of black-listed UserIds (optional)

The template also supports black-list and white-list. If a white-list is provided, the engine will include only those users in its recommendation.
Likewise, if a black-list is provided, the engine will exclude those users in its recommendation.

## Documentation

May refer to http://predictionio.incubator.apache.org/templates/similarproduct/quickstart/ with difference mentioned above.

## Development Notes

### import sample data

```
$ python data/import_eventserver.py --access_key <your_access_key>
```

### sample queries

normal:

```
curl -H "Content-Type: application/json" \
-d '{ "users": ["u1", "u3", "u10", "u2", "u5", "u31", "u9"], "num": 10}' \
http://localhost:8000/queries.json \
-w %{time_connect}:%{time_starttransfer}:%{time_total}
```

```
curl -H "Content-Type: application/json" \
-d '{
  "users": ["u1", "u3", "u10", "u2", "u5", "u31", "u9"],
  "num": 10
}' \
http://localhost:8000/queries.json \
-w %{time_connect}:%{time_starttransfer}:%{time_total}
```

```
curl -H "Content-Type: application/json" \
-d '{
  "users": ["u1", "u3", "u10", "u2", "u5", "u31", "u9"],
  "num": 10,
  "whiteList": ["u21", "u26", "u40"]
}' \
http://localhost:8000/queries.json \
-w %{time_connect}:%{time_starttransfer}:%{time_total}
```

```
curl -H "Content-Type: application/json" \
-d '{
  "users": ["u1", "u3", "u10", "u2", "u5", "u31", "u9"],
  "num": 10,
  "blackList": ["u21", "u26", "u40"]
}' \
http://localhost:8000/queries.json \
-w %{time_connect}:%{time_starttransfer}:%{time_total}
```

unknown user:

```
curl -H "Content-Type: application/json" \
-d '{ "users": ["unk1", "u3", "u10", "u2", "u5", "u31", "u9"], "num": 10}' \
http://localhost:8000/queries.json \
-w %{time_connect}:%{time_starttransfer}:%{time_total}
```


all unknown users:

```
curl -H "Content-Type: application/json" \
-d '{ "users": ["unk1", "unk2", "unk3", "unk4"], "num": 10}' \
http://localhost:8000/queries.json \
-w %{time_connect}:%{time_starttransfer}:%{time_total}
```
