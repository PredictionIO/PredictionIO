---
title: Engine Development - Troubleshoot
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

Apache PredictionIO provides the following features to help you
debug engines during development cycle.

## Stop Training between Stages

By default `pio train` runs through the whole training process including
[DataSource, Preparator and Algorithm](/templates/recommendation/dase/). To
speed up the development and debug cycle, you can stop the process after each
stage to verify it has completed correctly.

If you have modified DataSource and want to confirm the TrainingData is
generated as expected, you can run `pio train` with `--stop-after-read` option:

```
pio train --stop-after-read
```

This would stop the training process after the TrainingData is generated.

For example, if you are running [Recommendation
Template](/templates/recommendation/quickstart/), you should see the the
training process stops after the TrainingData is printed.

```
[INFO] [CoreWorkflow$] TrainingData:
[INFO] [CoreWorkflow$] ratings: [1501] (List(Rating(3,0,4.0), Rating(3,1,4.0))...)
...
[INFO] [CoreWorkflow$] Training interrupted by org.apache.predictionio.workflow.StopAfterReadInterruption.
```

Similarly, you can stop the training after the Preparator phase by using
--stop-after-prepare option and it would stop after PreparedData is generated:

```
pio train --stop-after-prepare
```

##  Sanity Check

You can extend a trait `SanityCheck` and implement the method
`sanityCheck()` with your error checking code. The `sanityCheck()` is called
when the data is generated. This can be applied to `TrainingData`, `PreparedData` and the `Model` classes, which are outputs of DataSource's `readTraining()`, Preparator's `prepare()` and Algorithm's `train()` methods, respectively.

For example, one frequent error with the Recommendation Template is that the
TrainingData is empty because the DataSource is not reading data correctly. You
can add the check of empty data inside the `sanityCheck()` function. You can
easily add other checking logic into the `sanityCheck()` function based on your
own needs. Also, If you implement `toString()` method in your TrainingData. You can call
`toString()` inside `sanityCheck()` to print out some data for visual checking.

For example, to print TrainingData to console and check if the `ratings` is empty, you can
do the following:

```scala
import org.apache.predictionio.controller.SanityCheck // ADDED

class TrainingData(
  val ratings: RDD[Rating]
) extends Serializable with SanityCheck { // EXTEND SanityCheck
  override def toString = {
    s"ratings: [${ratings.count()}] (${ratings.take(2).toList}...)"
  }

  // IMPLEMENT sanityCheck()
  override def sanityCheck(): Unit = {
    println(toString())
    // add your other checking here
    require(!ratings.take(1).isEmpty, s"ratings cannot be empty!")
  }
}
```

You may also use together with --stop-after-read flag to debug the DataSource:

```
pio build
pio train --stop-after-read
```

If your data is empty, you should see the following error thrown by the
`sanityCheck()` function:

```
[INFO] [CoreWorkflow$] Performing data sanity check on training data.
[INFO] [CoreWorkflow$] org.template.recommendation.TrainingData supports data sanity check. Performing check.
Exception in thread "main" java.lang.IllegalArgumentException: requirement failed: ratings cannot be empty!
	at scala.Predef$.require(Predef.scala:233)
	at org.template.recommendation.TrainingData.sanityCheck(DataSource.scala:73)
	at org.apache.predictionio.workflow.CoreWorkflow$$anonfun$runTypelessContext$7.apply(Workflow.scala:474)
	at org.apache.predictionio.workflow.CoreWorkflow$$anonfun$runTypelessContext$7.apply(Workflow.scala:465)
	at scala.collection.immutable.Map$Map1.foreach(Map.scala:109)
  ...
```

You can specify the `--skip-sanity-check` option to turn off sanityCheck:

```
pio train --stop-after-read --skip-sanity-check
```

You should see the checking is skipped such as the following output:

```
[INFO] [CoreWorkflow$] Data sanity checking is off.
[INFO] [CoreWorkflow$] Data Source
...
[INFO] [CoreWorkflow$] Training interrupted by org.apache.predictionio.workflow.StopAfterReadInterruption.
```

## Engine Status Page

After run `pio deploy`, you can access the engine status page by go to same URL and port of the deployed engine with your browser, which is "http://localhost:8000" by default. In the engine status page, you can find the Engine information, and parameters of each DASE components. In particular, you can also see the "Model" trained by the algorithm based on how `toString()` method is implemented in the Algorithm's Model class.

## pio-shell

Apache PredictionIO also provides `pio-shell` in which you can
easily access Apache PredictionIO API, Spark context and Spark API
for quickly testing code or debugging purposes.

To bring up the shell, simply run:

```
$ pio-shell --with-spark
```

(`pio-shell` is available inside `bin/` directory of installed Apache
PredictionIO directory, you should be able to access it if you have
added PredictionIO/bin into your environment variable `PATH`)

Note that the Spark context is available as variable `sc` inside the shell.

For example, to get the events of `MyApp1` using PEventStore API inside the pio-shell and collect them into an array `c`. run the following in the shell:

```
> import org.apache.predictionio.data.store.PEventStore
> val eventsRDD = PEventStore.find(appName="MyApp1")(sc)
> val c = eventsRDD.collect()
```

Then you should see following returned in the shell:

```
...
15/05/18 14:24:42 INFO DAGScheduler: Job 0 finished: collect at <console>:24, took 1.850779 s
c: Array[org.apache.predictionio.data.storage.Event] = Array(Event(id=Some(AaQUUBsFZxteRpDV_7fDGQAAAU1ZfRW1tX9LSWdZSb0),event=$set,eType=item,eId=i42,tType=None,tId=None,p=DataMap(Map(categories -> JArray(List(JString(c2), JString(c1), JString(c6), JString(c3))))),t=2015-05-15T21:31:19.349Z,tags=List(),pKey=None,ct=2015-05-15T21:31:19.354Z), Event(id=Some(DjvP3Dnci9F4CWmiqoLabQAAAU1ZfROaqdRYO-pZ_no),event=$set,eType=user,eId=u9,tType=None,tId=None,p=DataMap(Map()),t=2015-05-15T21:31:18.810Z,tags=List(),pKey=None,ct=2015-05-15T21:31:18.817Z), Event(id=Some(DjvP3Dnci9F4CWmiqoLabQAAAU1ZfRq7tsanlemwmZQ),event=view,eType=user,eId=u9,tType=Some(item),tId=Some(i25),p=DataMap(Map()),t=2015-05-15T21:31:20.635Z,tags=List(),pKey=None,ct=2015-05-15T21:31:20.639Z), Event(id=Some(DjvP3Dnci9F4CWmiqoLabQAAAU1ZfR...
```
