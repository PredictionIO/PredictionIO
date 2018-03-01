---
title: Using Alternative Algorithm
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

The classification template uses the Naive Bayes algorithm by default. You can easily add and use other MLlib classification algorithms. The following will demonstrate how to add the [MLlib Random Forests algorithm](https://spark.apache.org/docs/latest/mllib-ensembles.html) into the engine.

You can find the complete modified source code [here](https://github.com/apache/predictionio/tree/develop/examples/scala-parallel-classification/add-algorithm).

## Create a new file RandomForestAlgorithm.scala

Locate `src/main/scala/NaiveBayesAlgorithm.scala` under your engine directory, which should be /MyClassification if you are following the [Classification QuickStart](/templates/classification/quickstart/).  Copy `NaiveBayesAlgorithm.scala` and create a new file `RandomForestAlgorithm.scala`. You will modify this file and follow the instructions below to define a new RandomForestAlgorithm class.

##  Define the algorithm class and parameters

In 'RandomForestAlgorithm.scala', import the MLlib Random Forests algorithm by changing the following lines:

Original

```scala
import org.apache.spark.mllib.classification.NaiveBayes
import org.apache.spark.mllib.classification.NaiveBayesModel
```

Change to:

```scala
import org.apache.spark.mllib.tree.RandomForest // CHANGED
import org.apache.spark.mllib.tree.model.RandomForestModel // CHANGED
```

These are the necessary classes in order to use the MLLib's Random Forest algorithm.

Modify the `AlgorithmParams` class for the Random Forest algorithm:

```scala
// CHANGED
case class RandomForestAlgorithmParams(
  numClasses: Int,
  numTrees: Int,
  featureSubsetStrategy: String,
  impurity: String,
  maxDepth: Int,
  maxBins: Int
) extends Params
```

This class defines the parameters of the Random Forest algorithm (which later you can specify the value in engine.json). Please refer to [MLlib  documentation](https://spark.apache.org/docs/latest/mllib-ensembles.html) for the description and usage of these parameters.

Modify the `NaiveBayesAlgorithm` class to `RandomForestAlgorithm`. The changes are:

* The new `RandomForestAlgorithmParams` class is used as parameter.
* `RandomForestModel` is used in type parameter. This is the model returned by the Random Forest algorithm.
* the `train()` function is modified and it returns the `RandomForestModel` instead of `NaiveBayesModel`.
* the `predict()` function takes the `RandomForestModel` as input.



```scala
// extends P2LAlgorithm because the MLlib's RandomForestModel doesn't
// contain RDD.
class RandomForestAlgorithm(val ap: RandomForestAlgorithmParams) // CHANGED
  extends P2LAlgorithm[PreparedData, RandomForestModel, // CHANGED
  Query, PredictedResult] {

  // CHANGED
  def train(sc: SparkContext, data: PreparedData): RandomForestModel = {
    // CHANGED
    // Empty categoricalFeaturesInfo indicates all features are continuous.
    val categoricalFeaturesInfo = Map[Int, Int]()
    RandomForest.trainClassifier(
      data.labeledPoints,
      ap.numClasses,
      categoricalFeaturesInfo,
      ap.numTrees,
      ap.featureSubsetStrategy,
      ap.impurity,
      ap.maxDepth,
      ap.maxBins)
  }

  def predict(
    model: RandomForestModel, // CHANGED
    query: Query): PredictedResult = {

    val label = model.predict(Vectors.dense(
      Array(query.attr0, query.attr1, query.attr2)
    ))
    PredictedResult(label)
  }

}
```
Note that the MLlib Random Forest algorithm takes the same training data as the Naive Bayes algorithm (ie, RDD[LabeledPoint]) so you don't need to modify the `DataSource` and `PreparedData` classes. If the new algorithm to be added requires different types of training data, then you need to modify these classes accordingly to accommodate your new algorithm.
##  Update Engine.scala

Modify the EngineFactory to add the new algorithm class `RandomForestAlgorithm` you just defined and give it a name `"randomforest"`. The name will be used in `engine.json` to specify which algorithm to use.

```scala
object ClassificationEngine extends IEngineFactory {
  def apply() = {
    new Engine(
      classOf[DataSource],
      classOf[Preparator],
      Map("naive" -> classOf[NaiveBayesAlgorithm],
        "randomforest" -> classOf[RandomForestAlgorithm]), // ADDED
      classOf[Serving])
  }
}
```

This engine factory now returns an engine with two algorithms and they are named as `"naive"` and `"randomforest"` respectively.

##  Update engine.json

In order to use the new algorithm, you need to modify `engine.json` to specify the name of the algorithm and the parameters.

Update the engine.json to use **randomforest**:

```json
...
"algorithms": [
  {
    "name": "randomforest",
    "params": {
      "numClasses": 4,
      "numTrees": 5,
      "featureSubsetStrategy": "auto",
      "impurity": "gini",
      "maxDepth": 4,
      "maxBins": 100
    }
  }
]
...
```

The engine now uses **MLlib Random Forests algorithm** instead of the default Naive Bayes algorithm. You are ready to build, train and deploy the engine as described in [quickstart](/templates/classification/quickstart/).

```
$ pio build
$ pio train
$ pio deploy
```

INFO: To switch back using Naive Bayes algorithm, simply modify engine.json.
