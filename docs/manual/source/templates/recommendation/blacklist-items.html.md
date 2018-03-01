---
title: Filter Recommended Items by Blacklist in Query (Recommendation)
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

Let's say you want to supply a backList for each query to exclude some items from recommendation (For example, in the browsing session, the user just added some items to shopping cart, or you have a list of items you want to filter out, you may want to supply blackList in Query). This how-to will demonstrate how you can do it.

You can find the complete modified source code [here](https://github.com/apache/predictionio/tree/develop/examples/scala-parallel-recommendation/blacklist-items).

Note that you may also use [E-Commerce Recommendation Template](/templates/ecommercerecommendation/quickstart/) which supports this feature by default.

If you are looking for filtering out items based on the specific user-to-item events logged by EventServer (eg. filter all items which the user has "buy" events on), you can use the [E-Commerce Recommendation Template](/templates/ecommercerecommendation/quickstart/). Please refer to the algorithm parameters "unseenOnly" and "seenEvents" of the E-Commerce Recommenation Template.

## Add Query Parameter

First of all we need to specify query parameter to send items ids that the user has already seen.
Lets modify `case class Query` in MyRecommendation/src/main/scala/***Engine.scala***:

```scala
case class Query(
  user: String,
  num: Int,
  blackList: Set[String] // ADDED
)
```

## Filter the Data

Then we need to change the code that computes recommendation score to filter out the seen items.
Lets modify class MyRecommendation/src/main/scala/***ALSModel.scala***. Just add the following two methods to that class.

```scala
import com.github.fommil.netlib.BLAS.{getInstance => blas} // ADDED

...

  // ADDED
  def recommendProductsWithFilter(user: Int, num: Int, productIdFilter: Set[Int]) = {
    val filteredProductFeatures = productFeatures
      .filter { case (id, _) => !productIdFilter.contains(id) } // (*)
    recommend(userFeatures.lookup(user).head, filteredProductFeatures, num)
      .map(t => Rating(user, t._1, t._2))
  }

  // ADDED
  private def recommend(
      recommendToFeatures: Array[Double],
      recommendableFeatures: RDD[(Int, Array[Double])],
      num: Int): Array[(Int, Double)] = {
    val scored = recommendableFeatures.map { case (id, features) =>
      (id, blas.ddot(features.length, recommendToFeatures, 1, features, 1))
    }
    scored.top(num)(Ordering.by(_._2))
  }

...

```

Please make attention that method `recommend` is the copy of method `org.apache.spark.mllib.recommendation.MatrixFactorizationModel#recommend`. We can't reuse this because it’s private.
Method `recommendProductsWithFilter` is the almost full copy of `org.apache.spark.mllib.recommendation.MatrixFactorizationModel#recommendProducts` method. The difference only is the line with commentary ‘(*)’ where we apply filtering.

## Put It All Together

Next we need to invoke our new method with filtering when we query recommendations.
Lets modify method `predict` in MyRecommendation/src/main/scala/***ALSAlgorithm.scala***:

```scala
  def predict(model: ALSModel, query: Query): PredictedResult = {
    // Convert String ID to Int index for Mllib
    model.userStringIntMap.get(query.user).map { userInt =>
      // create inverse view of itemStringIntMap
      val itemIntStringMap = model.itemStringIntMap.inverse
      // recommendProductsWithFilter() returns Array[MLlibRating], which uses item Int
      // index. Convert it to String ID for returning PredictedResult
      val blackList = query.blackList.flatMap(model.itemStringIntMap.get) // ADDED
      val itemScores = model
        .recommendProductsWithFilter(userInt, query.num, blackList) // MODIFIED
        .map (r => ItemScore(itemIntStringMap(r.product), r.rating))
      PredictedResult(itemScores)
    }.getOrElse{
      logger.info(s"No prediction for unknown user ${query.user}.")
      PredictedResult(Array.empty)
    }
  }
```

## Test the Result

Then we can build/train/deploy the engine and test the result:

The query

```bash
curl \
-H "Content-Type: application/json" \
-d '{ "user": "1", "num": 4 }' \
http://localhost:8000/queries.json
```

will return the result

```json
{
    "itemScores": [{
        "item": "32",
        "score": 13.405593705856901
    }, {
        "item": "90",
        "score": 10.980439687813178
    }, {
        "item": "75",
        "score": 10.748973860065737
    }, {
        "item": "1",
        "score": 9.769636099226231
    }]
}
```

Lets say that the user has seen the `32` item.

```bash
curl \
-H "Content-Type: application/json" \
-d '{ "user": "1", "num": 4, "blackList": ["32"] }' \
http://localhost:8000/queries.json
```

will return the result

```json
{
    "itemScores": [{
        "item": "90",
        "score": 10.980439687813178
    }, {
        "item": "75",
        "score": 10.748973860065737
    }, {
        "item": "1",
        "score": 9.769636099226231
    }, {
        "item": "49",
        "score": 8.653951817512265
    }]
}
```

without item `32`.
