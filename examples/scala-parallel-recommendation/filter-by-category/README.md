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

# Recommendation Template With Filtering by Category

This engine template is based on the Recommendation Template version v0.1.2. It's modified so that each item has a set
of categories, queries include a `categories` field and results only include items in any of the specified categories.

## Documentation

Please refer to http://predictionio.incubator.apache.org/templates/recommendation/quickstart/

## Development Notes

### Sample data

This example uses two different files from the MovieLens 100k data. [Download](http://files.grouplens.org/datasets/movielens/ml-100k.zip)

The first file is the ratings data file, which is a tab separated file. The first 3 fields are used: user id, item id
and rating.

The second file is the items data file, in where different fields are separated by the `|` character. The fields of
interest are the first one (item id) and 19 category fields, starting at the 6th one and going till the end. Each
of these fields has the value `1` if the item is the given category, and `0` otherwise.

### Importing sample data

The `data/import_eventserver.py` script allows importing the sample data. Valid arguments are:

* `--url`: Event server URL
* `--access_key` App access key
* `--ratings_file`: Ratings file path
* `--items_file`: Items file path

### Changes to Engine.scala

Added `category` field to the `Query` class:

```scala
case class Query(
  user: String,
  num: Int,
  categories: Array[String]
) extends Serializable
```

### Changes to DataSource.scala

* Introduced class `Item`:

```scala
case class Item(
  id: String,
  categories: List[String]
)
```

* Modified class `TrainingData` to include items:

```scala
class TrainingData(
  val items: RDD[Item],
  val ratings: RDD[Rating]
) extends Serializable {
  override def toString = {
    s"items: [${items.count()}] (${items.take(2).toList}...)" +
    s" ratings: [${ratings.count()}] (${ratings.take(2).toList}...)"
  }
}
```

* Modified `readTraining` function so that it loads items:

```scala
    ...
    val itemsRDD: RDD[Item] = eventsDb.aggregateProperties(
      appId = dsp.appId,
      entityType = "item"
    )(sc).map {
      case (entityId, properties) =>
        try {
          Item(id = entityId, categories = properties.get[List[String]]("categories"))
        } catch {
          case e: Exception =>
            logger.error(s"Failed to get properties ${properties} of" +
              s" item ${entityId}. Exception: ${e}.")
            throw e
        }
    }
    ...
```

### Changes to Preparator.scala

* Added `items` field to class `PreparedData`. Pass `items` value from training data.

```scala
class PreparedData(
  val items: RDD[Item],
  val ratings: RDD[Rating]
) extends Serializable
```

```scala
    new PreparedData(items = trainingData.items, ratings = trainingData.ratings)
```

### Changes to ALSModel.scala

* Added `categoryItemsMap` field to ALSModel class

```scala
class ALSModel(
    override val rank: Int,
    override val userFeatures: RDD[(Int, Array[Double])],
    override val productFeatures: RDD[(Int, Array[Double])],
    val userStringIntMap: BiMap[String, Int],
    val itemStringIntMap: BiMap[String, Int],
    val categoryItemsMap: Map[String, Set[Int]])
```

* Added a function that recommends products whose ID is in a list of sets

```scala
  def recommendProductsFromCategory(user: Int, num: Int, categoryItems: Array[Set[Int]]) = {
    val filteredProductFeatures = productFeatures
      .filter { case (id, _) => categoryItems.exists(_.contains(id)) }
    recommend(userFeatures.lookup(user).head, filteredProductFeatures, num)
      .map(t => Rating(user, t._1, t._2))
  }

  private def recommend(
      recommendToFeatures: Array[Double],
      recommendableFeatures: RDD[(Int, Array[Double])],
      num: Int): Array[(Int, Double)] = {
    val recommendToVector = new DoubleMatrix(recommendToFeatures)
    val scored = recommendableFeatures.map { case (id,features) =>
      (id, recommendToVector.dot(new DoubleMatrix(features)))
    }
    scored.top(num)(Ordering.by(_._2))
  }
```

### Changes to ALSAlgorithm.scala

* Find set of categories

```scala
    ...
    val categories = data.items.flatMap(_.categories).distinct().collect().toSet
    ...
```

* Find set of items of each category

```scala
    ...
    val categoriesMap = categories.map { category =>
      category -> data.items
        .filter(_.categories.contains(category))
        .map(item => itemStringIntMap(item.id))
        .collect()
        .toSet
    }.toMap
    ...
```

* Find list of sets of items on which we are interested, use new `recommendProductsFromCategory` function:

```scala
      ...
      val categoriesItems = query.categories.map { category =>
        model.categoryItemsMap.getOrElse(category, Set.empty)
      }
      // recommendProductsFromCategory() returns Array[MLlibRating], which uses item Int
      // index. Convert it to String ID for returning PredictedResult
      val itemScores = model.recommendProductsFromCategory(userInt, query.num, categoriesItems)
        .map (r => ItemScore(itemIntStringMap(r.product), r.rating))
      ...
```

### Example Request

The script `data/send_query.py` has been modified to represent the updated query structure:

```python
print engine_client.send_query({"user": "1", "num": 4, "categories": ["action", "western"]})
```
