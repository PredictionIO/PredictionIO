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

# Similar Product Template Modified to Add Explicit Rate Event

This example engine is based on Similar Product Tempplate version v0.1.2  and is modified to add Explicit Rate Event to training data.

For example, An User would rate an item with a score or rating.The rating is used to train the model.   

## Documentation

Please refer to http://predictionio.incubator.apache.org/templates/similarproduct/quickstart/



## Development Notes

### Changes to DataSource.scala

1) class "Rating" is created.
```
case class Rating(
  user: String,
  item: String,
  rating: Double,
  t:Long
)
```

2) "rateEventsRDD" is initialized by filtering events of type "rate" from Events database as shown below.

```
   val rateEventsRDD: RDD[RateEvent] = eventsDb.find(
        appId = dsp.appId,
        entityType = Some("user"),
        eventNames = Some(List("rate")...
       
   val rateEvent = try {
        event.event match {
        case "rate" => RateEvent(
        user = event.entityId,
        item = event.targetEntityId.get,
        rating = event.properties.get[Double]("rating")...
```

### Changes to Preparator.scala

1) val "rateEvents" is added to class "PreparedData"

```
   class PreparedData(
    val users: RDD[(String, User)],
    val items: RDD[(String, Item)],
    val rateEvents: RDD[RateEvent]
```

2) val "rateEvents" is initialized in the Object of class "PreparedData".

```
   new PreparedData(
    users = trainingData.users,
    items = trainingData.items,
    rateEvents = trainingData.rateEvents)
```

### Changes to ALSAlgorithm.scala

1) Changed the Signature of "train" method  to include SparkContext and edited  method definition to specify the   debug message.

```
   def train(sc:SparkContext ,data: PreparedData): ALSModel = {
    require(!data.rateEvents.take(1).isEmpty,
    s"rateEvents in PreparedData cannot be empty." +
```

2) MlibRatings are initialized from rateEvents.

```
   val mllibRatings = data.rateEvents
```

3) Invoke "ALS.train" method to train explicit rate events.
```
   val m = ALS.train(
    ratings = mllibRatings,
    rank = ap.rank,
    iterations = ap.numIterations,
    lambda = ap.lambda,
    blocks = -1,
    seed = seed)
```

4) Define "rateEvent"  RDD to filter rate events from events.

```
   val rateEventsRDD: RDD[RateEvent] = eventsDb.find(...)
```

5) if a user may rate same item with different value at different times,use the latest value for this case.

```
    .reduceByKey { case (v1, v2) => // MODIFIED
       // if a user may rate same item with different value at different times,
       // use the latest value for this case.
       // Can remove this reduceByKey() if no need to support this case.
       val (rating1, t1) = v1
       val (rating2, t2) = v2
       // keep the latest value
       if (t1 > t2) v1 else v2
      }
```

6) persist  mlibRating.
```
   .map { case ((u, i), (rating, t)) => // MODIFIED
         // MLlibRating requires integer index for user and item
         MLlibRating(u, i, rating) // MODIFIED
        }.cache()
```


7) Add "rateEvent"  to class "TrainingData".

```
   class TrainingData(
    val users: RDD[(String, User)],
    val items: RDD[(String, Item)],
    val rateEvents: RDD[RateEvent]
 )
```

8) Add "rateEvent"  to object "TrainingData".

```
   new TrainingData(
    users = usersRDD,
    items = itemsRDD,
    rateEvents = rateEventsRDD)
```



