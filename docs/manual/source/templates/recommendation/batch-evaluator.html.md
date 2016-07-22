---
title: Batch Persistable Evaluator (Recommendation)
---

This how-to tutorial would explain how you can also use `$pio eval` to persist predicted result for a batch of queries. Please read the [Evaluation](/templates/recommendation/evaluation/) to understand the usage of DataSoure's `readEval()` and the Evaluation component first.

WARNING: This tutorial is based on some experimental and developer features, which may be changed in future release.

NOTE: This tutorial is based on Recommendation template version v0.3.2


## 1. Modify DataSource

Modify DataSource's `readEval()` to generate the batch Queries which you want to run batch predict.

```scala

  override
  def readEval(sc: SparkContext)
  : Seq[(TrainingData, EmptyEvaluationInfo, RDD[(Query, ActualResult)])] = {
    // This function only return one evaluation data set

    // Create your own queries here. Below are provided as examples.
    // for example, you may get all distinct user id from the trainingData to create the Query
    val batchQueries: RDD[Query] = sc.parallelize(
      Seq(
        Query(user = "1", num = 10),
        Query(user = "3", num = 15),
        Query(user = "5", num = 20)
      )
    )

    val queryAndActual: RDD[(Query, ActualResult)] = batchQueries.map (q =>
      // the ActualResult contain dummy empty rating array
      // because we not interested in Actual result for batch predict purpose.
      (q, ActualResult(Array()))
    )

    val evalDataSet = (
      readTraining(sc),
      new EmptyEvaluationInfo(),
      queryAndActual
    )

    Seq(evalDataSet)
  }

```

NOTE: Alternatively, you can create a new DataSource extending original DataSource. Then you can add the new one in Engine.scala and specify which one to use in engine.json.

<!-- TODO add more details -->

## 2. Add a new Evaluator

Create a new file `BatchPersistableEvaluator.scala`. Unlike the `MetricEvaluator`, this Evaluator simply writes the Query and correpsonding PredictedResult to the output directory without performaning any metrics calculation.

Note that output directory is specified by the variable `outputDir`.

```scala
package org.template.recommendation

import org.apache.predictionio.controller.EmptyEvaluationInfo
import org.apache.predictionio.controller.Engine
import org.apache.predictionio.controller.EngineParams
import org.apache.predictionio.controller.EngineParamsGenerator
import org.apache.predictionio.controller.Evaluation
import org.apache.predictionio.controller.Params
import org.apache.predictionio.core.BaseEvaluator
import org.apache.predictionio.core.BaseEvaluatorResult
import org.apache.predictionio.workflow.WorkflowParams

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import org.json4s.DefaultFormats
import org.json4s.Formats
import org.json4s.native.Serialization

import grizzled.slf4j.Logger

class BatchPersistableEvaluatorResult extends BaseEvaluatorResult {}

class BatchPersistableEvaluator extends BaseEvaluator[
  EmptyEvaluationInfo,
  Query,
  PredictedResult,
  ActualResult,
  BatchPersistableEvaluatorResult] {
  @transient lazy val logger = Logger[this.type]

  // A helper object for the json4s serialization
  case class Row(query: Query, predictedResult: PredictedResult)
    extends Serializable

  def evaluateBase(
    sc: SparkContext,
    evaluation: Evaluation,
    engineEvalDataSet: Seq[(
      EngineParams,
      Seq[(EmptyEvaluationInfo, RDD[(Query, PredictedResult, ActualResult)])])],
    params: WorkflowParams): BatchPersistableEvaluatorResult = {

    /** Extract the first data, as we are only interested in the first
      * evaluation. It is possible to relax this restriction, and have the
      * output logic below to write to different directory for different engine
      * params.
      */

    require(
      engineEvalDataSet.size == 1, "There should be only one engine params")

    val evalDataSet = engineEvalDataSet.head._2

    require(evalDataSet.size == 1, "There should be only one RDD[(Q, P, A)]")

    val qpaRDD = evalDataSet.head._2

    // qpaRDD contains 3 queries we specified in readEval, the corresponding
    // predictedResults, and the dummy actual result.

    /** The output directory. Better to use absolute path if you run on cluster.
      * If your database has a Hadoop interface, you can also convert the
      * following to write to your database in parallel as well.
      */
    val outputDir = "batch_result"

    logger.info("Writing result to disk")
    qpaRDD
      .map { case (q, p, a) => Row(q, p) }
      .map { row =>
        // Convert into a json
        implicit val formats: Formats = DefaultFormats
        Serialization.write(row)
      }
      .saveAsTextFile(outputDir)

    logger.info(s"Result can be found in $outputDir")

    new BatchPersistableEvaluatorResult()
  }
}
```


## 3. Define Evaluation and EngineParamsGenerator object


Create a new file `BatchEvaluation.scala`. Note that the new `BatchPersistableEvaluator` is used. The `BatchEngineParamsList` specifies the parameters of the engine.

Modify the appName parameter in `DataSourceParams` to match your app name.

```scala
package org.template.recommendation

import org.apache.predictionio.controller.EngineParamsGenerator
import org.apache.predictionio.controller.EngineParams
import org.apache.predictionio.controller.Evaluation

object BatchEvaluation extends Evaluation {
  // Define Engine and Evaluator used in Evaluation

  /**
    * Specify the new BatchPersistableEvaluator.
    */
  engineEvaluator =
    (RecommendationEngine(), new BatchPersistableEvaluator())
}

object BatchEngineParamsList extends EngineParamsGenerator {
  // We only interest in a single engine params.
  engineParamsList = Seq(
    EngineParams(
      dataSourceParams =
        DataSourceParams(appName = "INVALID_APP_NAME", evalParams = None),
      algorithmParamsList = Seq(("als", ALSAlgorithmParams(
        rank = 10,
        numIterations = 20,
        lambda = 0.01,
        seed = Some(3L))))))
}

```

## 4. build and run

Run the following command to build

```
$ pio build
```

After the build is successful, you should see the following outputs:

```
[INFO] [Console$] Your engine is ready for training.
```

To run the `BatchEvaluation` with `BatchEngineParamsList`, run the following command:

```
$ pio eval org.template.recommendation.BatchEvaluation   org.template.recommendation.BatchEngineParamsList
```

You should see the following outputs:

```
[INFO] [BatchPersistableEvaluator] Writing result to disk
[INFO] [BatchPersistableEvaluator] Result can be found in batch_result
[INFO] [CoreWorkflow$] Updating evaluation instance with result: org.template.recommendation.BatchPersistableEvaluatorResult@2f886889
[INFO] [CoreWorkflow$] runEvaluation completed
```

You should find the batch queries and the predicted results in the output directory `batch_result/`.
