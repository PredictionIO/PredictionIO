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

# Similar Product Template With Filter by Item Year support

This example engine is based on Similar Product Tempplate version v0.1.1 and is modified to support filter recommendation by the item property 'year'.

For example, recommend movies after year 1990.

## Documentation

Please refer to http://predictionio.incubator.apache.org/templates/similarproduct/quickstart/

## Development Notes

### Sample data

The movie lens 100k data which is in below format:

UserID::MovieID::Rating::Timestamp

### import ML-100K sample data

Import movielens data using below repository
https://github.com/k4hoo/systest/tree/master/0.8/ml100k/demo-movielens

```
$ python -m batch_import <Access Key> http://127.0.0.1:7070
```

### Changes to Engine.scala


1) Added “recommendFromYear” attribute to the Query class. we can pass the “recommendFromYear” attribute from the query request.

```scala
case class Query(
  items: List[String],
  num: Int,
  categories: Option[Set[String]],
  whiteList: Option[Set[String]],
  blackList: Option[Set[String]],
  recommendFromYear: Option[Int]
) extends Serializable
```

2)  Added “year” attribute to the class ItemScore.

```scala
case class ItemScore(
  item: String,
  score: Double,
  year: Int
) extends Serializable

```

### Changes to DataSource.scala

1) Added attribute “year” to the class Item

```scala
case class Item(categories: Option[List[String]],year: Int)
```

2) In the eventsDb.aggregateProperties, adding year property

```scala
  Item(categories = properties.getOpt[List[String]]("categories"),year = properties.get[Int]("year"))
```

### Changes to ALSAlgorihm.scala


1) In the predict method, passing “recommendFromYear” attribute to the isCandidateItem method

```scala
  isCandidateItem(
    i = i,
    items = model.items,
    categories = query.categories,
    queryList = queryList,
    whiteList = whiteList,
    blackList = blackList,
    recommendFromYear = query.recommendFromYear
  )
```

2) In “isCandidateItem” method, verifying if Item’s year is greater than “recommendFromYear” attribute.

```scala
  private def isCandidateItem(
    i: Int,
    items: Map[Int, Item],
    categories: Option[Set[String]],
    queryList: Set[Int],
    whiteList: Option[Set[Int]],
    blackList: Option[Set[Int]],
    recommendFromYear: Option[Int]
  ): Boolean = {
    whiteList.map(_.contains(i)).getOrElse(true) &&
    blackList.map(!_.contains(i)).getOrElse(true) &&
    // discard items in query as well
    (!queryList.contains(i)) &&
    // filter categories
    items(i).year > recommendFromYear.getOrElse(1) &&
    categories.map { cat =>
      items(i).categories.map { itemCat =>
        // keep this item if has ovelap categories with the query
        !(itemCat.toSet.intersect(cat).isEmpty)
      }.getOrElse(false) // discard this item if it has no categories
    }.getOrElse(true)
  }
```

3)  In the predict method, returning year as well as part of ItemScore

```scala
    val itemScores = topScores.map { case (i, s) =>
      new ItemScore(
        item = model.itemIntStringMap(i),
        score = s,
        year = model.items(i).year
      )
    }

    new PredictedResult(itemScores)
```

### Example Request

```
curl -H "Content-Type: application/json" \
-d '{ "items": ["171"], "num": 10, "recommendFromYear":1990 }' \
http://localhost:8000/queries.json
```
