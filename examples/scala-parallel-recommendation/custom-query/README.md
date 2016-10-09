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

# Filtering result set on custom item field (Recommendation)

## Import data to pio engine

By default the recommendation template reads the rate and buy user events and the user itself. You can modify the default DataSource to read your custom item with specified list of properties.

First off all you have to import your events to the pio event server.

You can use ImportDataScript.scala to import users, movies and rate events from [movielenses database](http://grouplens.org/datasets/movielens/). 
Make sure that data files are in `UTF-8` encoding.

This command line tool accepts 2 args:

 1. app access key and it is mandatory
 2. pio engine url. default is `http://localhost:7070`
 
For example in the sbt console: `> runMain org.template.recommendation.ImportDataScript.scala <access_key>`

## Modify the engine.

This example is based on v0.1.1 of [scala parallel recommendation template](https://github.com/PredictionIO/template-scala-parallel-recommendation/)

In this example we modify DataSource to read custom event with one property.

* Modify the Query data structure with your needs, at this example we added item creation year as a query/filter param:

`case class Query(user: String, num: Int, creationYear: Option[Int] = None)`

* Define the Item case class which will describe your data:

`case class Item(creationYear: Option[Int])`

* Define the utility unmarshaller to read the list of properties to your structure:

```
object ItemMarshaller {
 def unmarshall(properties: DataMap): Option[Item] =
   Some(Item(properties.getOpt[Int]("creationYear")))
}
```

* Modify the TrainingData class to hold your custom items:

`class TrainingData(val ratings: RDD[Rating], val items: RDD[(String, Item)])`
 
* Modify the readTraining method in the DataSource to read your custom item:

```
val itemsRDD = eventsDb.aggregateProperties(
    appId = dsp.appId,
    entityType = "item"
  )(sc).flatMap { case (entityId, properties) ⇒
    ItemMarshaller.unmarshall(properties).map(entityId → _)
  }
```

* Modify the ASLModel to hold your custom items to have possibility filter them.

```
class ALSModel(
 val productFeatures: RDD[(Int, Array[Double])],
 val itemStringIntMap: BiMap[String, Int],
 val items: Map[Int, Item])
```


* Modify train method in ALSAlgorithm to match you items with numeric ids that are needed by the algo.

```
val items: Map[Int, Item] = data.items.map { case (id, item) ⇒
   (itemStringIntMap(id), item)
}.collectAsMap.toMap
```

* Define the filterItems method in ALSAlgorithm to filter the predicted result set according the query.

```
private def filterItems(selectedScores: Array[(Int, Double)],
                       items: Map[Int, Item],
                       query: Query) =
 selectedScores.view.filter { case (iId, _) ⇒
   items(iId).creationYear.map(icr ⇒ query.creationYear.forall(icr >= _))
     .getOrElse(true)
 }
```

* Modify the predict method in the ALSAlgorithm to filter predicted result set:

`val filteredScores = filterItems(indexScores, model.items, query)`

* And the last step could be to modify the serving to sort recommended items by the year of movie creation(our custom property) as Hagay Gazuli mentioned in the [google group](https://groups.google.com/forum/#!searchin/predictionio-user/created$20%7Csort:date/predictionio-user/LEHxuc0Bu_0/W9RkAApvivsJ).

```
class Serving extends LServxing[Query, PredictedResult] {
  override def serve(query: Query,
            predictedResults: Seq[PredictedResult]): PredictedResult =
    predictedResults.headOption.map { result ⇒
      val preparedItems = result.itemScores
        .sortBy { case ItemScore(item, score, year) ⇒ year }(
          Ordering.Option[Int].reverse)
      new PredictedResult(preparedItems)
    }.getOrElse(new PredictedResult(Array.empty[ItemScore]))
}
```

* Now you can filter your recommendation by items that were made after some certain year:

```> curl -H 'Content-Type: application/json' '127.0.0.1:8000/queries.json' -d '{"user":100, "num":5, "creationYear":1990}' | python -m json.tool```

Where result of curl is piped to python json.tool lib just for convenience to pretty print the response from engine:
```
    "itemScores": [
        {
            "creationYear": 1996,
            "item": "831",
            "score": 518.9319563470217
        },
        {
            "creationYear": 1996,
            "item": "1619",
            "score": 15.321792791296401
        },
        {
            "creationYear": 1994,
            "item": "1554",
            "score": 628.1994336041231
        },
        {
            "creationYear": 1993,
            "item": "736",
            "score": 419.3508956666954
        },
        {
            "creationYear": 1991,
            "item": "627",
            "score": 498.28818189885175
        }
    ]
}
```

That's it! Now your recommendation engine is using filtering on custom item field on predicted result set.


