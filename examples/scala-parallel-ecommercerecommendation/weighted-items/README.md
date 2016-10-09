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

# E-Commerce Recommendation Template With Weighted Items

This engine template is based on the E-Commerce Recommendation Template v0.1.1. It has been modified so that
each item can be given a different weight.

By default, items have a weight of 1.0. Giving an item a weight greater than
1.0 will make them appear more often and can be useful for i.e. promoted products. An item can also be given
a weight smaller than 1.0 (but bigger than 0), in which case it will be recommended less often than originally. Weight
values smaller than 0.0 are invalid.

## Documentation

Please refer to http://predictionio.incubator.apache.org/templates/ecommercerecommendation/quickstart/

## Development Notes

### Weight constraint event

Item weights are specified by means of a `weightedItems` constraint type event , which includes the weight for all items
which don't have the default weight of 1.0. At any given time, only the last such `weightedItems` event is taken into
account.

The event design has been optimized for the use case where there may be many different items that are to be adjusted
by the same percentage, an so it's based on a list of objects, each containing a list of item IDs and their weight:

```
{
  "event" : "$set",
  "entityType" : "constraint"
  "entityId" : "weightedItems",
  "properties" : {
    weights: [
      {  
        "items": [ "i4", "i14"],
        "weight" : 1.2,
      },
      {
        "items": [ "i11"],
        "weight" : 1.5,
      }
    ]
  },
  "eventTime" : "2015-02-17T02:11:21.934Z"
}
```

### Changes to ALSAlgorithm.scala

* Added a case class to represent each group items which are given the same weight:

```scala
// Item weights are defined according to this structure so that groups of items can be easily changed together
case class WeightsGroup(
  items: Set[String],
  weight: Double
)
```

* Extract the sequence of `WeightsGroup`s defined in the last `weightedItems` event:

```scala
    // Get the latest constraint weightedItems. This comes in the form of a sequence of WeightsGroup
    val groupedWeights = lEventsDb.findSingleEntity(
      appId = ap.appId,
      entityType = "constraint",
      entityId = "weightedItems",
      eventNames = Some(Seq("$set")),
      limit = Some(1),
      latest = true,
      timeout = 200.millis
    ) match {
      case Right(x) =>
        if (x.hasNext)
          x.next().properties.get[Seq[WeightsGroup]]("weights")
        else
          Seq.empty
      case Left(e) =>
        logger.error(s"Error when reading set weightedItems event: ${e}")
        Seq.empty
    }
```

* Transform the sequence of `WeightsGroup`s into a `Map[Int, Double]` that we can easily query to extract the weight
given to an item, using its `Int` index. For undefined items, their weight is 1.0.

```scala
    // Transform groupedWeights into a map of index -> weight that we can easily query
    val weights: Map[Int, Double] = (for {
      group <- groupedWeights
      item <- group.items
      index <- model.itemStringIntMap.get(item)
    } yield (index, group.weight))
      .toMap
      .withDefaultValue(1.0)
```

* Adjust scores according to item weights:

```scala
            val originalScore = dotProduct(uf, feature.get)
            // Adjusting score according to given item weights
            val adjustedScore = originalScore * weights(i)
            (i, adjustedScore)
```

* Pass map of weights to `predictNewUser` function and adjust scores similarly

```scala
  private
  def predictNewUser(
    model: ALSModel,
    query: Query,
    whiteList: Option[Set[Int]],
    blackList: Set[Int],
    weights: Map[Int, Double]): Array[(Int, Double)] = {
```

```scala
          val originalScore = recentFeatures.map { rf =>
            cosine(rf, feature.get) // feature is defined
          }.sum
          // Adjusting score according to given item weights
          val adjustedScore = originalScore * weights(i)
          (i, adjustedScore)
```

```scala
      predictNewUser(
        model = model,
        query = query,
        whiteList = whiteList,
        blackList = finalBlackList,
        weights = weights
      )
```

### Setting constraint "weightedItems"

You can set the constraint *weightedItems* by simply sending an event to Event Server.

For example, say, you wanna adjust the score of items "i4", "i14" with a weight of 1.2 and item "i11" with weight of 1.5:

```
$ curl -i -X POST http://localhost:7070/events.json?accessKey=zPkr6sBwQoBwBjVHK2hsF9u26L38ARSe19QzkdYentuomCtYSuH0vXP5fq7advo4 \
-H "Content-Type: application/json" \
-d '{
  "event" : "$set",
  "entityType" : "constraint"
  "entityId" : "weightedItems",
  "properties" : {
    "weights": [
      {
        "items": ["i4", "i14"],
        "weight": 1.2
      },
      {
        "items": ["i11"],
        "weight": 1.5
      }
    ]
  }
  "eventTime" : "2015-02-17T02:11:21.934Z"
}'
```

Note that only latest set constraint is used (based on eventTime), which means that if you create another new constraint event, the previous constraint won't have any effect anymore. For example, after you send the following event, only scores of items "i2" and "i10" will be adjusted by the weights and previous constraint for items "i4", "i14"," i11" won't be used anymore.

```
$ curl -i -X POST http://localhost:7070/events.json?accessKey=<ACCESS KEY> \
-H "Content-Type: application/json" \
-d '{
  "event" : "$set",
  "entityType" : "constraint"
  "entityId" : "weightedItems",
  "properties" : {
    "weights": [
      {
        "items": ["i2", "i10"],
        "weight": 1.5
      }
    ]
  }
  "eventTime" : "2015-02-20T04:56:78.123Z"
}'
```

To clear the constraint, simply set empty weights array. i.e:

```
curl -i -X POST http://localhost:7070/events.json?accessKey=<ACCESS KEY> \
-H "Content-Type: application/json" \
-d '{
  "event" : "$set",
  "entityType" : "constraint"
  "entityId" : "weightedItems",
  "properties" : {
    "weights": []
  }
  "eventTime" : "2015-02-20T04:56:78.123Z"
}'
```


You can also use SDK to send these events as shown in the sample set_weights.py script.

### set_weights.py script

A `set_weights.py` has been created to add weight to some of the elements. Usage:

```
$ python data/set_weights.py --access_key <your_access_key>
```
