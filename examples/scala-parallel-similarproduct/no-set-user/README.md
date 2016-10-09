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

# Similar Product Template without sending '$set' events for users

In some cases, if you don't need to keep track the user ID being created/deleted or user properties changes with events, then you can simplify the template as described in this example to get rid of sending '$set' events for users. The user Id can be extracted from the user-to-item events (eg. view events). You can find the complete source code in src/ directory.

This example engine is based on Similar Product Template version v0.1.3.

## Documentation

Please refer to http://predictionio.incubator.apache.org/templates/similarproduct/quickstart/

## Development Notes

There you can find several steps that we have done to modify original Similar Product template

### Changes to import_eventserver.py

For importing sample data, you can remove the following code that generates '$set' events for entities with type 'user'

```python
  for user_id in user_ids:
    print "Set user", user_id
    client.create_event(
      event="$set",
      entity_type="user",
      entity_id=user_id
    )
    count += 1
```

### Changes to DataSource.scala

Remove the following code

```scala
    // create a RDD of (entityID, User)
    val usersRDD: RDD[(String, User)] = eventsDb.aggregateProperties(
      appId = dsp.appId,
      entityType = "user"
    )(sc).map { case (entityId, properties) =>
      val user = try {
        User()
      } catch {
        case e: Exception => {
          logger.error(s"Failed to get properties ${properties} of" +
            s" user ${entityId}. Exception: ${e}.")
          throw e
        }
      }
      (entityId, user)
    }.cache()
```

Modify class 'TrainingData' that should looks like the following code:

```scala
class TrainingData(
  val items: RDD[(String, Item)],
  val viewEvents: RDD[ViewEvent]
) extends Serializable {
  override def toString = {
    s"items: [${items.count()} (${items.take(2).toList}...)]" +
    s"viewEvents: [${viewEvents.count()}] (${viewEvents.take(2).toList}...)"
  }
}
```

Then fix compilation issue in invocation 'TrainingData' constructor that should looks like following:

```
    new TrainingData(
      items = itemsRDD,
      viewEvents = viewEventsRDD
    )
```

### Changes to Preparator.scala

We removed RDD with users from class 'TrainingData' at the previous step.
So, we should apply the same changes for class 'PreparedData'.

The file Preparator.scala should looks like the following code:

```
class Preparator
  extends PPreparator[TrainingData, PreparedData] {
  def prepare(sc: SparkContext, trainingData: TrainingData): PreparedData = {
    new PreparedData(
      items = trainingData.items,
      viewEvents = trainingData.viewEvents)
  }
}
class PreparedData(
  val items: RDD[(String, Item)],
  val viewEvents: RDD[ViewEvent]
) extends Serializable
```

### Changes to ALSAlgorithm.scala

Remove the following code:

```scala
    require(!data.users.take(1).isEmpty,
      s"users in PreparedData cannot be empty." +
      " Please check if DataSource generates TrainingData" +
      " and Preprator generates PreparedData correctly.")
```

and modify the line where 'userStringIntMap' value is defined to extract the user ID from the viewEvents and create user String ID to Int Index BiMap. It should look like the following code:

```scala
val userStringIntMap = BiMap.stringInt(data.viewEvents.map(_.user))
```

## Test the Changes

```
$ pio build
$ pio train
$ pio deploy
```

After that you can try to make the same queries that were listed in the quickstart.
The result will be the same.
