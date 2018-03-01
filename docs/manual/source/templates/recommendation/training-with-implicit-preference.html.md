---
title: Training with Implicit Preference (Recommendation)
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

There are two types of user preferences:

- explicit preference (also referred as "explicit feedback"), such as "rating" given to item by users.
- implicit preference (also referred as "implicit feedback"), such as "view" and "buy" history.

MLlib ALS provides the `setImplicitPrefs()` function to set whether to use implicit preference. The ALS algorithm takes RDD[Rating] as training data input. The Rating class is defined in Spark MLlib library as:

```
case class Rating(user: Int, product: Int, rating: Double)
```

By default, the recommendation template sets `setImplicitPrefs()` to `false` which expects explicit rating values which the user has rated the item.

To handle implicit preference, you can set `setImplicitPrefs()` to `true`. In this case, the "rating" value input to ALS is used to calculate the confidence level that the user likes the item. Higher "rating" means a stronger indication that the user likes the item.

The following provides an example of using implicit preference. You can find the complete modified source code [here](https://github.com/apache/predictionio/tree/develop/examples/scala-parallel-recommendation/train-with-view-event).

### Training with view events

For example, if the more number of times the user has viewed the item, the higher confidence that the user likes the item. We can aggregate the number of views and use this as the "rating" value.

First, we can modify `DataSource.scala` to aggregate the number of views of the user on the same item:

```scala

  def getRatings(sc: SparkContext): RDD[Rating] = {

    val eventsRDD: RDD[Event] = PEventStore.find(
      appName = dsp.appName,
      entityType = Some("user"),
      eventNames = Some(List("view")), // MODIFIED
      // targetEntityType is optional field of an event.
      targetEntityType = Some(Some("item")))(sc)

    val ratingsRDD: RDD[Rating] = eventsRDD.map { event =>
      try {
        val ratingValue: Double = event.event match {
          case "view" => 1.0 // MODIFIED
          case _ => throw new Exception(s"Unexpected event ${event} is read.")
        }
        // MODIFIED
        // key is (user id, item id)
        // value is the rating value, which is 1.
        ((event.entityId, event.targetEntityId.get), ratingValue)
      } catch {
        case e: Exception => {
          logger.error(s"Cannot convert ${event} to Rating. Exception: ${e}.")
          throw e
        }
      }
    }
    // MODIFIED
    // sum all values for the same user id and item id key
    .reduceByKey { case (a, b) => a + b }
    .map { case ((uid, iid), r) =>
      Rating(uid, iid, r)
    }.cache()

    ratingsRDD
  }

  override
  def readTraining(sc: SparkContext): TrainingData = {
    new TrainingData(getRatings(sc))
  }

```

NOTE: You may put the view count aggregation logic in `ALSAlgorithm`'s `train()` instead, depending on your needs.


Then, we can modify ALSAlgorithm.scala to set `setImplicitPrefs` to `true`:

```scala

class ALSAlgorithm(val ap: ALSAlgorithmParams)
  extends PAlgorithm[PreparedData, ALSModel, Query, PredictedResult] {

  ...

  def train(sc: SparkContext, data: PreparedData): ALSModel = {

    ...

    // If you only have one type of implicit event (Eg. "view" event only),
    // set implicitPrefs to true
    // MODIFIED
    val implicitPrefs = true
    val als = new ALS()
    als.setUserBlocks(-1)
    als.setProductBlocks(-1)
    als.setRank(ap.rank)
    als.setIterations(ap.numIterations)
    als.setLambda(ap.lambda)
    als.setImplicitPrefs(implicitPrefs)
    als.setAlpha(1.0)
    als.setSeed(seed)
    als.setCheckpointInterval(10)
    val m = als.run(mllibRatings)

    new ALSModel(
      rank = m.rank,
      userFeatures = m.userFeatures,
      productFeatures = m.productFeatures,
      userStringIntMap = userStringIntMap,
      itemStringIntMap = itemStringIntMap)
  }

  ...

}

```

Now the recommendation engine can train a model with implicit preference events.

#### [Next: Filter Recommended Items by Blacklist in Query](blacklist-items.html)
