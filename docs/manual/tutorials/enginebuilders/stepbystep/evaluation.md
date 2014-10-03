---
layout: docs
title: Evaluation
---

# Evaluation

In this tutorial, we will demonstrate how to implement an evaluation *Metrics*
component to run *Offline Evaluation* for the *Engine*. We will continue to use
the Item Recommendation Engine developed in Tutorial1 as example and implement a
*Metrics* which computes the Root Mean Square Error.

## Step 1 - Training and Test Set Split

To run *Offline Evaluation*, we need *Training* and *Test Set* data. We will
modify `DataSource.java` to do a random split of the rating data to generate
the *Test Set*. For demonstration purpose, the modified `DataSource.java` is put
under directory `tutorial3/`.

Recall that `io.prediction.controller.java.LJavaDataSource` takes the
following type parameters:

```java
public abstract class LJavaDataSource<DSP extends Params,DP,TD,Q,A>
```
- `DSP`: *DataSource Parameters* class.
- `DP`: *Data Parameters* class. It is used to describe the generated *Training
  Data* and the Test Data *Query and Actual*, which is used by *Metrics* during
  evaluation.
- `TD`: *Training Data* class.
- `Q`: Input *Query* class.
- `A`: *Actual* result class.

The *Actual* result is used by *Metrics* to compare with *Prediciton* outputs to
compute the score. In this tutorial, the *Actual* result is also the rating
value, which is `Float` type. Since we don't have any *Data Parameters* defined,
we can simply use `Object`.

You can find the implementation in `tutorial3/DataSource.java`:

```java
public class DataSource extends LJavaDataSource<
  DataSourceParams, Object, TrainingData, Query, Float> {
  //...
  @Override
  public Iterable<Tuple3<Object, TrainingData, Iterable<Tuple2<Query, Float>>>> read() {
    // ...
  }
}
```

As explained in earlier tutorials, the `read()` method should read data from the
source (e.g. database or text file, etc) and return the *Training Data* (`TD`)
and *Test Data* (`Iterable<Tuple2<Q, A>>`) with a *Data Parameters* (`DP`) associated
with this *Training and Test Data Set*.

Note that the `read()` method's return type is `Iterable` because it could
return one or more of *Training and Test Data Set*. For example, we may want to
evaluate the engine with multiple iterations of random training and test data
split. In this case, each set corresponds to one such random split.

Note that the *Test Data* is actually an `Iterable` of input *Query* and
*Actual* result. During evaluation, PredictionIO sends the *Query* to the engine
and retrieve *Prediction* output, which will be evaluated against the *Actual*
result by the *Metric*.


## Step 2 - Metrics

We will implement a Root Mean Square Error (RMSE) metric. You can find the
implementation in `Metrics.java`. The *Metrics* extends
`io.prediction.controller.java.JavaMetrics`, which requires the following type
parameters:

```java
public abstract class JavaMetrics<MP extends Params,DP,Q,P,A,MU,MR,MMR>
```
- `MP`: *Metrics Parameters* class.
- `DP`: *Data Parameters* class.
- `Q`: Input *Query* class.
- `P`: *Prediction* output class.
- `A`: *Actual* result class.
- `MU`: *Metric Unit* class.
- `MR`: *Metric Result* class.
- `MMR`: *Multiple Metric Result* class.

and overrides the following methods:

```java
public abstract MU computeUnit(Q query, P predicted, A actual)

public abstract MR computeSet(DP dataParams, Iterable<MU> metricUnits)

public abstract MMR computeMultipleSets(Iterable<scala.Tuple2<DP,MR>> input)
```

The method `computeUnit()` computes the *Metric Unit (MU)* for each *Prediction*
and *Actual* results of the input *Query*.

For this RMSE metric, `computeUnit()` returns the square error of each
predicted rating and actual rating.

```java
@Override
public Double computeUnit(Query query, Float predicted, Float actual) {
  logger.info("Q: " + query.toString() + " P: " + predicted + " A: " + actual);
  // return squared error
  double error;
  if (predicted.isNaN())
    error = -actual;
  else
    error = predicted - actual;
  return (error * error);
}
```

The method `computeSet()` takes all of the *Metric Unit (MU)* of the same set to
compute the *Metric Result (MR)* for this set.

For this RMSE metric, `computeSet()` calculates the square root mean of all
square errors of the same set and then return it.

```java
@Override
public Double computeSet(Object dataParams, Iterable<Double> metricUnits) {
  double sum = 0.0;
  int count = 0;
  for (double squareError : metricUnits) {
    sum += squareError;
    count += 1;
  }
  return Math.sqrt(sum / count);
}
```

The method `computeMultipleSets()` takes the *Metric Results* of all sets to do
a final computation and returns *Multiple Metric Result*. PredictionIO will
display this final *Multiple Metric Result* in the terminal.

In this tutorial, it simply combines all *Metric Results* as String and return
it.

```java
@Override
public String computeMultipleSets(
  Iterable<Tuple2<Object, Double>> input) {
  return Arrays.toString(IteratorUtils.toArray(input.iterator()));
}
```

## Step 3 - Run Evaluation

To run evaluation with metric, simply add the `Metrics` class to the
`runEngine()` in `JavaWorkflow.runEngine()` (as shown in `Runner3.java`).

Because our `Metrics` class doesn't take parameter, `EmptyParams` class is used.

```java
JavaWorkflow.runEngine(
  (new EngineFactory()).apply(),
  engineParams,
  Metrics.class,
  new EmptyParams(),
  new WorkflowParamsBuilder().batch("MyEngine").verbose(3).build()
);
```

Execute the following command:

```
$ cd $PIO_HOME/examples/java-local-tutorial
$ ../../bin/pio run io.prediction.examples.java.recommendations.tutorial3.Runner3 -- -- data/test/ratings.csv
```
where `$PIO_HOME` is the root directory of the PredictionIO code tree.

You should see the following output when it finishes running.

```
2014-09-30 16:53:55,924 INFO  workflow.CoreWorkflow$ - CoreWorkflow.run completed.
2014-09-30 16:53:56,044 WARN  workflow.CoreWorkflow$ - java.lang.String is not a NiceRendering instance.
2014-09-30 16:53:56,053 INFO  workflow.CoreWorkflow$ - Saved engine instance with ID: nU81XwpjSl-F43-CHgwJZQ
```

To view the Metric Result (RMSE score), start the dashboard with the `pio dashboard` command:

```
$ cd $PIO_HOME/examples/java-local-tutorial
$ ../../bin/pio dashboard
```

Then point your browser to `localhost:9000` to view the result. You should see the result

```
[(null,1.0), (null,3.8078865529319543), (null,1.5811388300841898)]
```
in the page.

## Step 4 - Running with MovieLens 100K data set:

Run the following to fetch the data set, if you haven't already done so.
The `ml-100k` will be downloaded into the `data/` directory.

```
$ cd $PIO_HOME/examples/java-local-tutorial
$ ./fetch.sh
```

Re-run `Runner3` with the `ml-100k` data set:

```
$ ../../bin/pio run io.prediction.examples.java.recommendations.tutorial3.Runner3 -- -- data/ml-100k/u.data
```

You should see the following output when it finishes running.

```
2014-09-30 17:06:34,033 INFO  spark.SparkContext - Job finished: collect at Workflow.scala:597, took 0.103821 s
2014-09-30 17:06:34,033 INFO  workflow.CoreWorkflow$ - DataSourceParams: io.prediction.examples.java.recommendations.tutorial1.DataSourceParams@3b9f69ce
2014-09-30 17:06:34,033 INFO  workflow.CoreWorkflow$ - PreparatorParams: Empty
2014-09-30 17:06:34,034 INFO  workflow.CoreWorkflow$ - Algo: 0 Name: MyRecommendationAlgo Params: io.prediction.examples.java.recommendations.tutorial1.AlgoParams@76171b1
2014-09-30 17:06:34,034 INFO  workflow.CoreWorkflow$ - ServingParams: Empty
2014-09-30 17:06:34,035 INFO  workflow.CoreWorkflow$ - MetricsParams: Empty
2014-09-30 17:06:34,035 INFO  workflow.CoreWorkflow$ - [(null,1.052046904037191), (null,1.042766938101085), (null,1.0490312745374106)]
2014-09-30 17:06:34,035 INFO  workflow.CoreWorkflow$ - CoreWorkflow.run completed.
2014-09-30 17:06:34,152 WARN  workflow.CoreWorkflow$ - java.lang.String is not a NiceRendering instance.
2014-09-30 17:06:34,160 INFO  workflow.CoreWorkflow$ - Saved engine instance with ID: IjWc8yyDS3-9JyXGVuWVgQ
```

To view the Metric Result (RMSE score), start the dashboard with the `pio
dashboard` command:

```
$ cd $PIO_HOME/examples/java-local-tutorial
$ ../../bin/pio dashboard
```

Then point your browser to http://localhost:9000 to view the result. You
should see the result

```
[(null,1.052046904037191), (null,1.042766938101085), (null,1.0490312745374106)]
```
in the page.

Up to this point, you should be familiar with basic components of PredictionIO
(*DataSource*, *Algorithm* and *Metrics*) and know how to develop your
algorithms and prediction engines, deploy them and serve real time prediction
queries.

In the next tutorial, we will demonstrate how to use *Preparator* to do
pre-processing of *Training Data* for the *Algorithm*, incorporate multiple
*Algorithms* into the *Engine* and create a custom *Serving* component.

Next: [Combining Multiple Algorithms at Serving](combiningalgorithms.html)
