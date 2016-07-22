---
title: Customizing Data Preparator (Recommendation)
---


Data Preparator is where pre-processing actions occurs. For example, one may
want to remove some very popular items from the training data because she thinks
that these items may not help finding individual person's tastes or one may have
a black list of item that she wants to remove from the training data before
feeding it to the algorithm.

This section assumes that you have created a *MyRecommendation* engine based on
the [Recommendation Engine Template: QuickStart](/templates/recommendation/quickstart/). We will
demonstrate how to add a filtering logic to exclude a list of items in the
training data.

A sample black list file containing the items to be excluded is provided in
`./data/sample_not_train_data.txt`.

A full end-to-end example can be found on
[GitHub](https://github.com/PredictionIO/PredictionIO/tree/develop/examples/scala-parallel-recommendation/custom-prepartor).

## The Data Preparator Component

Recall [the DASE Architecture](/start/engines/), data is prepared by 2
components sequentially: *Data Source* and *Data Preparator*. *Data Source*
reads data from the data store of Event Server and then *Data Preparator*
prepares `RDD[Rating]` for the ALS algorithm.

You may modify any component in an engine template to fit your needs. This
example shows you how to add the filtering logics in Data Preparator.

## Modify the Preparator

The Data Preparator component can be found in `src/main/scala/Preparator.scala`
in the "MyRecommendation" directory. The unmodified version looks like the
following:

```scala
class Preparator
  extends PPreparator[TrainingData, PreparedData] {

  def prepare(sc: SparkContext, trainingData: TrainingData): PreparedData = {
    new PreparedData(ratings = trainingData.ratings)
  }
}
```

The `prepare` method simply passes the ratings from `TrainingData` to
`PreparedData`.

You can modify the `prepare` method to read a black list of items from a file
and remove them from `TrainingData`, so it becomes:

```scala
import scala.io.Source // ADDED

class Preparator
  extends PPreparator[TrainingData, PreparedData] {

  def prepare(sc: SparkContext, trainingData: TrainingData): PreparedData = {
    // MODIFIED HERE
    val noTrainItems = Source.fromFile("./data/sample_not_train_data.txt")
      .getLines.toSet
    // exclude noTrainItems from original trainingData
    val ratings = trainingData.ratings.filter( r =>
      !noTrainItems.contains(r.item)
    )
    new PreparedData(ratings)
  }
}
```

> We will show you how not to hardcode the path
`./data/sample_not_train_data.txt` soon.


## Deploy the Modified Engine

Now you can deploy the modified engine as described in [Quick
Start](quickstart.html).

Make sure the `appName` defined in the file `engine.json` matches your *App Name*:

```
...
"datasource": {
  "params" : {
    "appName": "YourAppName"
  }
},
...
```

To build *MyRecommendation* and deploy it as a service:

```
$ pio build
$ pio train
$ pio deploy
```

This will deploy an engine that binds to http://localhost:8000. You can visit
that page in your web browser to check its status.

Now, You can try to retrieve predicted results. To recommend 4 movies to user
whose ID is 1, send this JSON `{ "user": "1", "num": 4 }` to the deployed engine

```
$ curl -H "Content-Type: application/json" -d '{ "user": "1", "num": 4 }' http://localhost:8000/queries.json
```

and it will return a JSON of recommended movies.

```json
{
  "itemScores": [
    {"item": "22", "score": 4.072304374729956},
    {"item": "62", "score": 4.058482414005789},
    {"item": "75", "score": 4.046063009943821},
    {"item": "68", "score": 3.8153661512945325}
  ]
}
```

Congratulations! You have learned how to add customized logic to your Data
Preparator!

##  Adding Preparator Parameters

Optionally, you may want to take the hardcoded path
(`./data/sample_not_train_data.txt`) away from the source code.

PredictionIO offers `PreparatorParams` so you can read variable values from
`engine.json` instead.

Modify `src/main/scala/Preparator.scala` again in the *MyRecommendation*
directory to:

```scala
import org.apache.predictionio.controller.Params // ADDED

 // ADDED CustomPreparatorParams case class
case class CustomPreparatorParams(
  filepath: String
) extends Params

class Preparator(pp: CustomPreparatorParams) // ADDED CustomPreparatorParams
  extends PPreparator[TrainingData, PreparedData] {

  def prepare(sc: SparkContext, trainingData: TrainingData): PreparedData = {
    val noTrainItems = Source.fromFile(pp.filepath).getLines.toSet //CHANGED
    val ratings = trainingData.ratings.filter( r =>
      !noTrainItems.contains(r.item)
    )
    new PreparedData(ratings)
  }
}

```

In `engine.json`, you define the parameters `filepath` for the Data Preparator:

```json
{
  ...
  "preparator": {
    "params": {
      "filepath": "./data/sample_not_train_data.txt"
    }
  },
  ...
}
```

Try to build *MyRecommendation* and deploy it again:

```
$ pio build
$ pio train
$ pio deploy
```

You can change the `filepath` value without re-building the code next time.

#### [Next: Customizing Serving](customize-serving.html)
