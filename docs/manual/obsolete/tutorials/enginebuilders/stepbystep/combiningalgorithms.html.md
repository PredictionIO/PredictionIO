---
title: Serving - Combining Algorithms
---

# Serving - Combining Multiple Algorithms

At this point you have already had a sense of implementing, deploying, and
evaluating a recommendation system with collaborative filtering techniques.
However, this technique suffers from a cold-start problem where new items have
no user action history. In this tutorial, we introduce a feature-based
recommendation technique to remedy this problem by constructing a user-profile
for each user. In addition, Prediction.IO infrastructure allows you to combine
  multiple recommendation systems together in a single engine. For a
  history-rich items, the engine can use results from the collaborative
  filtering algorithm, and for history-absent items, the engine returns
  prediction from the feature-based recommendation algorithm. Moreover, we can
  ensemble multiple predictions too.

This tutorial guides you toward incorporating a feature-based algorithm into
the existing CF-based recommendation engine introduced in tutorials 1, 2 and 3.

All code can be found in the
[tutorial4/](https://github.com/PredictionIO/PredictionIO/tree/master/examples/java-local-tutorial/src/main/java/recommendations/tutorial4)
directory.

## Overview
In the previous tutorial, we have covered `DataSource` and `Algorithm` as
crucial parts of an engine. A complete engine workflow looks like the following
figure:

```
            DataSource.read
             (TrainingData)
                   v
           Preparator.prepare
             (PreparedData)
                   v
     +-------------+-------------+
     v             v             v
Algo1.train   Algo2.train   Algo3.train
  (Model1)      (Model2)      (Model3)
     v             v             v
Algo1.predict Algo2.predict Algo3.predict <- (Query)
(Prediction1) (Prediction2) (Prediction3)
     v             v             v
     +-------------+-------------+
                   v
              Serving.serve
              (Prediction)
```

`Preparator` is the class which preprocess the training data which will be used
by multiple algorithms. For example, it can be a NLP processor which generates
useful n-grams, or it can be some business logics.

Engine is designed to support multiple algorithms. They need to take the same
`PreparedData` as input for model construction, but each algorithm can have its
own model class). Algorithm takes a common `Query` as input and return a
`Prediction` as output.

Finally, the serving layer `Serving` combines result from multiple algorithms,
and possible apply some final business logic before returning.

This tutorial implements a simple `Preparator` for feature generation, a
feature based algorithm, and a serving layer which ensembles multiple
predictions.

## DataSource
We have to amend the
[`DataSource`](https://github.com/PredictionIO/PredictionIO/tree/master/examples/java-local-tutorial/src/main/java/recommendations/tutorial4/DataSource.java)
to take into account more information from MovieLens, as well as adding some
fake data for demonstration. We use the genre of movies as its feature vector.
This part is simliar to earlier tutorials.

```
$ cd $PIO_HOME/examples/java-local-tutorial
$ ../../bin/pio run org.apache.predictionio.examples.java.recommendations.tutorial4.Runner4a -- -- data/ml-100k/
```
where `$PIO_HOME` is the root directory of the PredictionIO code tree.

You should see

```
2014-09-30 17:16:45,515 INFO  spark.SparkContext - Job finished: collect at Workflow.scala:388, took 0.020437 s
2014-09-30 17:16:45,516 INFO  workflow.CoreWorkflow$ - Data Set 0
2014-09-30 17:16:45,516 INFO  workflow.CoreWorkflow$ - Params: Empty
2014-09-30 17:16:45,517 INFO  workflow.CoreWorkflow$ - TrainingData:
2014-09-30 17:16:45,517 INFO  workflow.CoreWorkflow$ - [TrainingData: rating.size=100003 genres.size=19 itemInfo.size=1685 userInfo.size=946]
2014-09-30 17:16:45,517 INFO  workflow.CoreWorkflow$ - TestingData: (count=0)
2014-09-30 17:16:45,518 INFO  workflow.CoreWorkflow$ - Data source complete
2014-09-30 17:16:45,518 INFO  workflow.CoreWorkflow$ - Preparator is null. Stop here
```


## Preparator
As we have read the raw data from `DataSource`, we can *preprocess* the raw
data into a more useable form. In this tutorial, we generate a feature vector
for movies based on its genre.

We need to implement two classes: `Preparator` and `PreparedData`.
`Preparator` is a class implementing a method `prepare` which transform
`TrainingData` into `PreparedData`; `PreparedData` is the output and the object
being passed to `Algorithms` for training. `PreparedData` can be anything, very
often it is equivalent to `TrainingData`, or subclass of it. Here,
[`PreparedData`](https://github.com/PredictionIO/PredictionIO/tree/master/examples/java-local-tutorial/src/main/java/recommendations/tutorial4/PreparedData.java)
is a subclass of `TrainingData`,
it adds a map from items (movies) to feature vectors. The merit of using
subclass is that, it makes the original `TrainingData` easily accessible.

The
[`Preparator`](https://github.com/PredictionIO/PredictionIO/tree/master/examples/java-local-tutorial/src/main/java/recommendations/tutorial4/Preparator.java)
class simply examines item info and extract a feature vector from item info.

After implementing these two classes, you can add them to the workflow and try
out if things are really working. Add the preparator class to the engine
builder, as shown in
[`Runner4b.java`](https://github.com/PredictionIO/PredictionIO/tree/master/examples/java-local-tutorial/src/main/java/recommendations/tutorial4/Runner4b.java):

```java
return new JavaEngineBuilder<
    TrainingData, EmptyParams, PreparedData, Query, Float, Object> ()
    .dataSourceClass(DataSource.class)
    .preparatorClass(Preparator.class)  // Add the new preparator
    .build();
```

And you can test it out with

```bash
$ cd $PIO_HOME/examples/java-local-tutorial
$ ../../bin/pio run org.apache.predictionio.examples.java.recommendations.tutorial4.Runner4b -- -- data/ml-100k/
```

You should see

```
2014-09-30 17:28:37,335 INFO  spark.SparkContext - Job finished: collect at WorkflowUtils.scala:179, took 0.209634 s
2014-09-30 17:28:37,335 INFO  workflow.CoreWorkflow$ - Prepared Data Set 0
2014-09-30 17:28:37,336 INFO  workflow.CoreWorkflow$ - Params: Empty
2014-09-30 17:28:37,336 INFO  workflow.CoreWorkflow$ - PreparedData: [TrainingData: rating.size=100003 genres.size=19 itemInfo.size=1685 userInfo.size=946 itemFeatures.size=1685 featureCount=19]
2014-09-30 17:28:37,336 INFO  workflow.CoreWorkflow$ - Preparator complete
2014-09-30 17:28:37,337 INFO  workflow.CoreWorkflow$ - Algo model construction
2014-09-30 17:28:37,337 INFO  workflow.CoreWorkflow$ - AlgoList has zero length. Stop here
```


## Feature-Based Algorithm
This algorithm creates a feature profile for every user using the feature
vector in `PreparedData`. More specifically, if a user has rated 5 stars on
*Toy Story* but 1 star on *The Crucible*, the user profile would reflect that
this user likes comedy and animation but dislikes drama.

The movie lens rating is an integer ranged from 1 to 5, we incorporate it into
the algorithm with the following parameters:

```java
public class FeatureBasedAlgorithmParams implements JavaParams {
  public final double min;
  public final double max;
  public final double drift;
  public final double scale;
  ...
}
```

We only consider rating from `min` to `max`, and we normalize the rating with
this function: `f(rating) = (rating - drift) * scale`. As each movie is
associated with a binary feature vector, the user feature vector is essentially
a rating-weighted sum of all movies (s)he rated.  After that, we normalize all
user feature vector by L-inf norm, this will ensure that user feature is bounded
by [-1, 1]. In laymen terms, -1 indicates that the user hates that feature,
whilst 1 suggests the opposite.  The following is a snippet of the [actual
code](https://github.com/PredictionIO/PredictionIO/tree/master/examples/java-local-tutorial/src/main/java/recommendations/tutorial4/FeatureBasedAlgorithm.java).
`data` is an instance of `PreparedData` that is passed as an argument to the
`train` function.

```java
for (Integer uid : data.userInfo.keySet()) {
  userFeatures.put(uid, new ArrayRealVector(data.featureCount));
  userActions.put(uid, 0);
}

for (TrainingData.Rating rating : data.ratings) {
  final int uid = rating.uid;
  final int iid = rating.iid;
  final double rate = rating.rating;

  // Skip features outside the range.
  if (!(params.min <= rate && rate <= params.max)) continue;

  final double actualRate = (rate - params.drift) * params.scale;
  final RealVector userFeature = userFeatures.get(uid);
  final RealVector itemFeature = data.itemFeatures.get(iid);
  userFeature.combineToSelf(1, actualRate, itemFeature);
}

// Normalize userFeatures by l-inf-norm
for (Integer uid : userFeatures.keySet()) {
  final RealVector feature = userFeatures.get(uid);
  feature.mapDivideToSelf(feature.getLInfNorm());
}
```

[Runner4c.java](https://github.com/PredictionIO/PredictionIO/tree/master/examples/java-local-tutorial/src/main/java/recommendations/tutorial4/Runner4c.java)
illustrates the engine factory up to this point. We use a default serving class
as we only have one algorithm. (We will demonstrate how to combine prediction
results from multiple algorithms later in this tutorial). We are able to define
[an end-to-end
engine](https://github.com/PredictionIO/PredictionIO/tree/master/examples/java-local-tutorialsrc/main/java/recommendations/tutorial4/SingleEngineFactory.java).

```bash
$ cd $PIO_HOME/examples/java-local-tutorial
$ ../../bin/pio run org.apache.predictionio.examples.java.recommendations.tutorial4.Runner4c -- -- data/ml-100k/
```

## Deployment

We can deploy this feature based engine just like tutorial 1. We have an [engine
JSON](https://github.com/PredictionIO/PredictionIO/tree/master/examples/java-local-tutorial/src/main/java/recommendations/tutorial4/single-algo-engine.json),
and we register it:

```bash
$ cd $PIO_HOME/examples/java-local-tutorial
$ ../../bin/pio register --engine-json src/main/java/recommendations/tutorial4/single-algo-engine.json
```

The script automatically recompiles updated code. You will need to re-run this
script if you have update any code in your engine.

### Specify Engine Parameters
We use the following JSON files for deployment.

1.  [datasource.json](https://github.com/PredictionIO/PredictionIO/tree/master/examples/java-local-tutorial/src/main/java/recommendations/tutorial4/single-jsons/datasource.json):

    ```json
    {
      "dir" :  "data/ml-100k/",
      "addFakeData": true
    }
    ```

2.  [algorithms.json](https://github.com/PredictionIO/PredictionIO/tree/master/examples/java-local-tutorial/src/main/java/recommendations/tutorial4/single-jsons/algorithms.json):

    ```json
    [
      {
        "name": "featurebased",
        "params": {
          "min": 1.0,
          "max": 5.0,
          "drift": 3.0,
          "scale": 0.5
        }
      }
    ]
    ```

    Recall that we support multiple algorithms. This JSON file is actually a
    list of *name-params* pair where the *name* is the identifier of algorithm
    defined in EngineFactory, and the *params* value corresponds to the algorithm
    parameter(s).


### Start training
The following command kick-starts the training, which will return an id when
the training is completed.

```bash
$ cd $PIO_HOME/examples/java-local-tutorial
$ ../../bin/pio train \
  --engine-json src/main/java/recommendations/tutorial4/single-algo-engine.json \
  --params-path src/main/java/recommendations/tutorial4/single-jsons
```

### Deploy server
As the training is completed, you can deploy a server

```bash
$ cd $PIO_HOME/examples/java-local-tutorial
$ ../../bin/pio deploy --engine-json src/main/java/recommendations/tutorial4/single-algo-engine.json
```

### Try a few things

Fake user -1 (see
[DataSource.FakeData](https://github.com/PredictionIO/PredictionIO/tree/master/examples/java-local-tutorial/src/main/java/recommendations/tutorial4/DataSource.java))
loves action movies. If we pass in item 27 (Bad Boys), we should get a high
rating (i.e. 1). You can use our script `bin/cjson` to send the JSON request.
The first parameter is the JSON request, and the second parameter is the server
address.

```bash
$ cd $PIO_HOME/examples/java-local-tutorial
$ ../../bin/cjson '{"uid": -1, "iid": 27}' http://localhost:8000/queries.json
```

Fake item -2 is a cold item (i.e. has no rating). But from the data, we know
that it is a movie catagorized under "Action" genre, hence, it should also have
a high rating with Fake user -1.

```bash
$ cd $PIO_HOME/examples/java-local-tutorial
$ ../../bin/cjson '{"uid": -1, "iid": -2}' http://localhost:8000/queries.json
```

However, there is nothing we can do with a cold user. Fake user -3 has no
rating history, we know nothing about him. If we request any rating with fake
user -3, we will get a NaN.
TODO: @Donald "NaN is not a valid double value as per JSON specification. To override this behavior, use GsonBuilder.serializeSpecialFloatingPointValues() method."

```bash
$ cd $PIO_HOME/examples/java-local-tutorial
$ ../../bin/cjson '{"uid": -3, "iid": 1}' http://localhost:8000/queries.json
```

## Multiple Algorithms
We have two algorithms available, one is a collaborative filtering algorithm and
the other is a feature-based algorithm. Prediction.IO allows you to create an
engine that ensembles multiple algorithms prediction, you may use feature-based
algorithm for cold-start items (as CF-based algos cannot handle items with no
ratings), and use both algorithms for others.

### Combining Algorithms Output

[Serving](https://github.com/PredictionIO/PredictionIO/tree/master/examples/java-local-tutorial/src/main/java/recommendations/tutorial4/Serving.java)
is the last step of the pipeline. It takes prediction results from all
algorithms, combine them and return. In the current case, we take an average of
all valid (i.e. not NaN) predictions. In the extreme case where all algorithms
return NaN, we also return NaN. Engine builders need to implement the `serve`
method. We demonstrate with our case:

```java
public Float serve(Query query, Iterable<Float> predictions) {
  float sum = 0.0f;
  int count = 0;

  for (Float v: predictions) {
    if (!v.isNaN()) {
      sum += v;
      count += 1;
    }
  }
  return (count == 0) ? Float.NaN : sum / count;
}
```

### Complete Engine Factory

[EngineFactory.java](https://github.com/PredictionIO/PredictionIO/tree/master/examples/java-local-tutorial/src/main/java/recommendations/tutorial4/EngineFactory.java)
demonstrates how to specify multiple algorithms in the same engine. When we add
algorithms to the builder instance, we also need to specify a String which is
served as the identifier. For example, we use "featurebased" for the
feature-based algorithm, and "collaborative" for the collaborative-filtering
algorithm.

```java
public class EngineFactory implements IJavaEngineFactory {
  public JavaEngine<TrainingData, EmptyParams, PreparedData, Query, Float, Object> apply() {
    return new JavaEngineBuilder<
      TrainingData, EmptyParams, PreparedData, Query, Float, Object> ()
      .dataSourceClass(DataSource.class)
      .preparatorClass(Preparator.class)
      .addAlgorithmClass("featurebased", FeatureBasedAlgorithm.class)
      .addAlgorithmClass("collaborative", CollaborativeFilteringAlgorithm.class)
      .servingClass(Serving.class)
      .build();
  }
}
```

Similar to the earlier example, we need to write [a
JSON](https://github.com/PredictionIO/PredictionIO/tree/master/examples/java-local-tutorial/src/main/java/recommendations/tutorial4/multiple-algo-engine.json)
for the engine, and register it with PredictionIO. Here's the content:

```json
{
  "id": "org.apache.predictionio.examples.java.recommendations.tutorial4.EngineFactory",
  "version": "0.8.2",
  "name": "FeatureBased Recommendations Engine",
  "engineFactory": "org.apache.predictionio.examples.java.recommendations.tutorial4.EngineFactory"
}
```

The following script register the engines. Important to note that, the script
also copies all related files (jars, resources) of this engine to a permanent
storage, if you have updated the engine code or add new dependencies, you need
to rerun this command.

```bash
$ cd $PIO_HOME/examples/java-local-tutorial
$ ../../bin/pio register --engine-json src/main/java/recommendations/tutorial4/multiple-algo-engine.json
```

Now, we can specify the engine instance by passing the set of parameters to the
engine. Our engine can support multiple algorithms, and in addition, it also
support multiple instance of the same algorithms. We illustrate with
[algorithms.json](https://github.com/PredictionIO/PredictionIO/tree/master/examples/java-local-tutorial/src/main/java/recommendations/tutorial4/jsons/algorithms.json):

```json
[
  {
    "name": "featurebased",
    "params": {
      "min": 1.0,
      "max": 5.0,
      "drift": 3.0,
      "scale": 0.5
    }
  },
  {
    "name": "featurebased",
    "params": {
      "min": 4.0,
      "max": 5.0,
      "drift": 3.0,
      "scale": 0.5
    }
  },
  {
    "name": "collaborative",
    "params": {
      "threshold": 0.2
    }
  }
]
```

This JSON contains three algorithm parameters. The first two correspond to the
feature-based algorithm, and the third corresponds to the collaborative
filtering algorithm. The first allows all 5 ratings, and the second allows only
ratings higher than or equals to 4. This gives a bit more weight on the
high-rating features. Once [all parameter files are
specified](https://github.com/PredictionIO/PredictionIO/tree/master/examples/java-local-tutorial//src/main/java/recommendations/tutorial4/jsons/),
we can start the training phase and start the API server:

```bash
$ cd $PIO_HOME/examples/java-local-tutorial
$ ../../bin/pio train \
  --engine-json src/main/java/recommendations/tutorial4/multiple-algo-engine.json \
  --params-path src/main/java/recommendations/tutorial4/jsons
...
2014-09-30 21:59:36,256 INFO  spark.SparkContext - Job finished: collect at Workflow.scala:695, took 3.961802 s
2014-09-30 21:59:36,529 INFO  workflow.CoreWorkflow$ - Saved engine instance with ID: Bp60PPk4SHqyeDzDPHbv-Q

$ ../../bin/pio deploy --engine-json src/main/java/recommendations/tutorial4/multiple-algo-engine.json
```

By default, the server starts on port 8000. Open it with your browser and you
will see all the meta information about this engine instance.

You can submit various queries to the server and see what you get.
