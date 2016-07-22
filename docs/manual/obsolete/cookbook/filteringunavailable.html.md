---
title: Customize Serving Layer 
---

This tutorial teaches how to implement custom filtering logic.
It is based [Movie Recommendation App with ItemRec Engine](/tutorials/engines/itemrec/movielens.html), we demonstrate how to add a custom filtering logic to the ItemRecommendation Engine.

Complete code example can be found in
`examples/scala-local-movielens-filtering`.
If you are too lazy to go through this tutorial but want to use this customized code, you can skip to the last section.

### Task

The ItemRec Engine recommends items to user.
Some items may run out of stock temporarily, we would like to remove them from the recommendation.

# Customizing the ItemRec Engine

Recall [the DASE Architecture](/enginebuilders), a PredictionIO engine has 4 main components: Data Source, Data Preparator, Algorithm, and Serving Layer.
When a Query comes in, it is passed to the Algorithm components for making Predictions (notice that we use plural as the infrastructure allows multiple algorithms to run concurrently), then the Serving component consolidates these
Predictions into one, and returns it.

The ItemRec Engine's component can be found it its static factory class
`org.apache.predictionio.engines.itemrec.ItemRecEngine`. It looks like the following:

```scala
object ItemRecEngine extends IEngineFactory {
  def apply() = {
    new Engine(
      classOf[EventsDataSource],
      classOf[ItemRecPreparator],
      Map("ncMahoutItemBased" -> classOf[NCItemBasedAlgorithm]),
      classOf[ItemRecServing]
    )
  }
}
```

To add filtering logic to this engine,
we will implement a new Serving component, which removes temporarily disabled
items from the Prediction made by Algorithms.
For simplicity, we assume the engine only has one algorithm, the serving logic doesn't need to handle consolidation.

## The Serving Interface
PredictionIO allows you to substitute any component in a prediction engine as long as interface is matched. In this case, the Serving component has to use
the Query and Prediction class defined by the original engine. The `serve` method performs the filting logic.

```scala
class TempFilter(val params: TempFilterParams)
    extends LServing[TempFilterParams, Query, Prediction] {
  override def serve(query: Query, predictions: Seq[Prediction])
  : Prediction = {
    // Our filtering logic
  }
}
```

We will store the disabled items in a file, one item_id per line. Every time the `serve` method is invoked, it removes items whose id can be found in the file.

> Notice that this is only for demonstration, reading from disk for every query leads to terrible system performance. User can implement more efficient I/O.

Then, we will implement a new engine factory using this new Serving component.

# Step-by-Step

Below are the step-by-step instruction of implementing a customize logic.


## Create a new engine project and copy builtin engine settings.

You can create new engine project in any directory.

```bash
# Create a new engine project
$ $PIO_HOME/bin/pio new scala-local-movielens-filtering
# Copy ItemRec Engine default settings to the same directory
$ $PIO_HOME/bin/pio instance --directory-name scala-local-movielens-filtering \
    org.apache.predictionio.engines.itemrec
$ cd scala-local-movielens-filtering
```

Delete unnecessary template files.

```bash
$ rm src/main/scala/Engine.scala
```

## Implement the new filtering component

We need to define one parameter: The filepath of the blacklist file.

```scala
case class TempFilterParams(val filepath: String) extends Params
```

The Serving component implementation is trivial. Every time the method `serve` is invoked, it reads the blacklisted file from disk. Then it removes these items from the Prediction.

```scala
class TempFilter(val params: TempFilterParams)
    extends LServing[TempFilterParams, Query, Prediction] {
  override def serve(query: Query, predictions: Seq[Prediction])
  : Prediction = {
    // Read blacklisted items from disk
    val disabledIids: Set[String] = Source.fromFile(params.filepath)
      .getLines()
      .toSet

    val prediction = predictions.head
    // prediction.items is a list of (item_id, score)-tuple
    prediction.copy(items = prediction.items.filter(e => !disabledIids(e._1)))
  }
}
```

## Define a new engine factory

We need to implement a new engine factory to include this filter. All we need to do is to copy and paste the ItemRec's factory, and replace the serving component with `TempFilter`.

```scala
object TempFilterEngine extends IEngineFactory {
  def apply() = {
    new Engine(
      classOf[EventsDataSource],
      classOf[ItemRecPreparator],
      Map("ncMahoutItemBased" -> classOf[NCItemBasedAlgorithm]),
      classOf[TempFilter]   // The only difference.
    )
  }
}
```

Lastly, to register the engine with Prediction.IO, we need to edit the `engine.json` file in the project root directory. Below is an example. Two important fields: `id` is a unique id in Prediction.IO to identify the engine, `engineFactory` is the classpath to the engine factory.

```json
{
  "id": "scala-local-movielens-filtering",
  "version": "0.0.1-SNAPSHOT",
  "name": "scala-local-movielens-filtering",
  "engineFactory": "myorg.TempFilterEngine"
}
```

This project depends on the builtin engines, hence in `build.sbt` under project root, add the following line to libraryDependencies.

```scala
libraryDependencies ++= Seq(
  ...
  "org.apache.predictionio"    %% "engines"       % "0.8.2" % "provided",
  ...
```

## Deploy the new engine

This process is equivalent to the register-train-deploy procedure of implementing a new engine.

### Update parameters files

When we create this project, we have copied the default parameters of the ItemRec Engine. They can be found under the directory `params`.

Specify the `<app_id>` you used for importing in file `params/datasource.json`.

```json
{
  "appId": <app_id>,
  ...
}
```

Specify the *full path* of the blacklisting file in `params/serving.json`.
Notice that this files don't yet have the field `filepath`, since it was the default parameter (which is empty) for the serving component in the ItemRec Engine.
We have prepared a sample file in `examples/scala-local-movielens-filtering/blacklisted.txt`
The file should looks like the following:

```json
{
  "filepath": "/home/pio/PredictionIO/examples/scala-local-movielens-filtering/blacklisted.txt"
}
```

### Register-train-deploy

#### Register the new engine

You need to run the following command every time you update the code.

```bash
$ $PIO_HOME/bin/pio register
...
2014-10-16 22:41:32,608 INFO  tools.RegisterEngine$ - Registering engine scala-local-movielens-filtering 0.0.1-SNAPSHOT
```

If the command ran successfully, you will see the log message saying "Registering engine...".

#### Train the engine instance

```bash
$ $PIO_HOME/bin/pio train -- --master spark://`hostname`:7077
...
2014-10-16 22:44:11,006 INFO  spark.SparkContext - Job finished: collect at Workflow.scala:698, took 1.381009 s
2014-10-16 22:44:11,343 INFO  workflow.CoreWorkflow$ - Saved engine instance with ID: FqsPp84mS6itmn0YoNFBUg

```

#### Deploy the engine instance

```bash
$PIO_HOME/bin/pio deploy -- --master spark://`hostname`:7077
...
[INFO] [10/16/2014 22:45:08.486] ... Bind successful. Ready to serve.
```

# Play with the customized engine

The engine can now serve live queries. With the sample file `blacklisted.txt`, items 272 and 123 are blacklisted. The new serving component `TempFilter` removes them from Prediction results. If we use the same query as in the
[Movie Recommendation App (see bottom of the page)](/tutorials/engines/itemrec/movielens.html),

```bash
$ curl -X POST -d '{"uid": "100", "n": 3}' http://localhost:8000/queries.json
{"items":[
  {"313":9.92607593536377},
  {"347":9.92170524597168}]}
```

Item 272 is removed from the Prediction result.

We can further test the new serving code by adding more items to the blacklist, suppose we add item 347 to the file, and re-submit the same query:

```bash
$ cat blacklisted.txt
272
123
347
$ curl -X POST -d '{"uid": "100", "n": 3}' http://localhost:8000/queries.json
{"items":[
  {"313":9.92607593536377}]}
```

Item 347 is filtered. Only one item left in the Prediction result.

> User may notice that this filtering is a *post-prediction filtering*. Meaning that it may return significantly less items than what is requested in `Query.n`.
> User should consider using a larger n in order to prevent all items being filtered.

# Side note: Use the code directly.

The above code can be found in
`examples/scala-local-movielens-filtering`. You can use it directly with

```bash
# Assuming you are at PredictionIO source root
$ cd examples/scala-local-movielens-filtering
# Edit datasource params to use the correct app_id
$ vim params/datasource.json
# Edit serving params to the full path of backlisted.txt
$ vim params/serving.json
# Register-train-deploy
$ $PIO_HOME/bin/pio register
...
$ $PIO_HOME/bin/pio train -- --master spark://`hostname`:7077
...
$ $PIO_HOME/bin/pio deploy -- --master spark://`hostname`:7077
...
```

At this point, you should be able to query the prediction server at `http://localhost:8000`.
