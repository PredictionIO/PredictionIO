---
title: Offline Evaluating the Prediction Results
---

<!--
# Evaluating the Prediction Results
-->

To chose the best algorithm and parameters, it is important to systematically
evaluate the quality of the engine instance.
In this section, we will explain how to carry out an offline evaluation of an
engine. We will use the MovieLens data set with the ItemRank Engine as
example. We will discuss

- In general, how to use Prediction.IO's evaluation framework;
- How to construct the testing set from raw training data;
- How to avoid look-ahead-bias by applying sliding window evaluation technique;
- How to construct the Query that best approximate the business need;
- How to choose the evaluation parameter set that refects the business
  objective.

# ItemRank Evaluation

This section discusses how to evaluate the [ItemRank
Engine](../../engines/itemrank). An ItemRank Engine makes a
personalized ranking among the items in the input query.

## What You Want To Evaluate

The goal of evaluation is to find a set of parameters with which a engine
instance is created that maximize certain objective function(s). An objective
function is usually tie with a business objective. For example,

- an e-commerce
company has an inventory of 1000+ items, but can only show 5 on their landing
page,
and the goal is to increase the click-thru-rate (hopefully the conversion rate)
of the top 5 items;

- another e-commerce company wants to sell its expiring items,
hence has add a time component to the weight function;

- yet another company may
only focus on long-term value of a customer, it only recommends what the user
likes the most (but maybe not profitable), etc.

Whilst the machine learning community has defined numerous quality measure (e.g.
precision, recall, MAP, MSE, etc.) for measuring prediction, there are many
other considerations when we conduct the evaluation. Generating the test set
can be tricky and bias-prone if we don't do it careful enough.
It is important to
make sure that the evaluation method we choose approximates the actual business
use case.

## Example Evaluation: Movie Lens

We create a fictitious company MLC using the [MovieLens data
set](../tutorials/engines/itemrec/movielens.html).
It provides movie recommendations to users. For a given time, the user comes to
the MLC's website, it displays all movies in its inventory to the user, rank by
some mechanism (i.e. MLC uses the ItemRank engine for predicting user
preferences).
MLC's goal is to maximize its long term value, i.e. users like coming back to
the company's website for movie recommendation.

The MovieLens data set contains the following information:

1. User data: userid, age, and occupation.

2. Item (movie) data: itemid, movie name, release date, and genre.

3. Rating data: userid, itemid, rating, timestamp.

To evaluate the quality of a prediction engine, we need to create two sets of
data.

1. Training set, the data used to train machine learning models. It generally
   includes [labelled data](http://en.wikipedia.org/wiki/Supervised_learning),
   in this case the rating assigned to movies by users. The ItemRank engine
   takes these labelled data for training.

2. Testing set, the data used to evaluate the quality of prediction engines.
   The testing set also contains labelled data, but it is *not* sent to
   prediction engines. Instead, they are used by the evaluator
   to evaluate the quality of the prediction engine.

### Sliding Window Evaluation

A common way to evaluate prediction is by using the sliding window method
(sometimes it is called rolling window method). Assuming the data set is
annotated by a timestamp, which is always the case if you are using Prediction.IO
Event Server, we can always divide the whole data set into training and testing
set using a cutoff timestamp. It is particularly appealing since 1. it can avoid
look-ahead bias; and 2. it mimics actual use case.

Suppose MLC retrains the prediction model every night, it uses all data in the
database and creates a new model, the new model is then used to serve queries in
the next day. The next night, MLC retrains again with the additional data, and
the new model is used to serve the traffic the day after next. Hopefully you
will get why people call it *sliding window*.

In the evaluation, we attempt to recreate the same scenario. We use three
parameters to define the sliding window:

Field | Description
---- | :------
`firstTrainingUntilTime` | The cutoff time for the first training data. Only events happened before this time will be used.
`evalDuration` | The duration of the test data. Events happened from `firstTrainingUntilTime` until `firstTrainingUntilTime + evalDuration` will be used to construct the *unseen* testing set.
`evalCount` | The number of training / testing sets used in the evaluation.

For example, the following parameters

```scala
EventsSlidingEvalParams(
  firstTrainingUntilTime = new DateTime(1998, 2, 1, 0, 0),
  evalDuration = Duration.standardDays(7),
  evalCount = 3)
```

create three training / testing sets of data (time is midnight):

Set | Training Event Time | Testing Event Time
--- | --- | ---
0 | [..., 1998-02-01) | [1998-02-01, 1998-02-08)
1 | [..., 1998-02-08) | [1998-02-08, 1998-02-15)
2 | [..., 1998-02-15) | [1998-02-15, 1998-02-22)

#### Training Data

The process of creating the training data is equivalent to the training process
we use for deploy, except that we only use events up to certain cutoff time. For
Set 0, the cutoff time is the 1998-02-01T00:00:00.

There is one caveat: in the MovieLens dataset, only rating data is annotated
with a timestamp. User data and item data is a static info. We have to
workaround this problem by imposing some assumption to the date. In this case,
we assume in the import code that all users existed since [the very beginning]
(https://github.com/PredictionIO/PredictionIO-Python-SDK/blob/5e14a6f3ea0e9fca52f16375e98219659b4de09b/examples/demo-movielens/batch_import.py#L24),
and items are created on [the release
date](https://github.com/PredictionIO/PredictionIO-Python-SDK/blob/5e14a6f3ea0e9fca52f16375e98219659b4de09b/examples/demo-movielens/batch_import.py#L58).

#### Testing Data

Now it comes to the core component about evaluation.
Conceptually, we compare the engine prediction with actual results.
To evaluate an engine instance, we need three pieces of data.

1. Query. It is same as the one we used for deployment, except that it
   doesn't not come from an external HTTP call, but is created by the data
   source. Note that, from an engine point of view, there is no difference
   between Query used in deployment or evaluation, they should be treated
   equally.

2. Predicted Result. It is also the same as the one we used for deployment, as
   it is the return value of the engine instance.

3. Actual Result. It is the ground truth. For MLC, it is the actual rating users
   gave to items. Actual Result is hidden from the prediction and serving
   components, it is exclusively used by the evaluation component to compute the
   quality of actual instance.

The question is how to construct Queries using MovieLens data set. The data set
only contain a sequence of ratings, but recall that our goal here is to evaluate
an ItemRank engine's quality. The input (i.e. Query) of the ItemRank engine
is a user id and a list of items, the output (i.e. Prediction) is a ranked list
of items. There is a mismatch between the data set and the I/O of the
prediction engine, it is meaningless to construct a Query for each rating as
ranking an single-item-query is an identical function.
Our task is to define a reasonable method to bridge this gap.

> Readers may find it weird that there is a huge logical gap between the data
> set and the actual usage. Indeed, from our experience, it happens very often.
> A common cause of the discrepencies is that, the user-item behaviorial data
> may not exist. For example, an e-commerence company may only have recorded the
> customer purchase for accounting reason, but other information like what items
> have been shown to the user, or the website outlook are not recorded.
> If the company wants to start using
> smart prediction service, it may need to bootstrap the prediction model with
> the purchase data, in which case we have to define a reasonable mechanism to
> mimic behavioral data from the purchase history.

There are multiple ways of creating Query set from the rating, for example:

- Create a Query per active user with the global active items (active user /
  items means that they have some rating within the testing period);

- Create a Query per user with all items;

- Create a Query per active user with all items rated by that user within the
  testing period;

- Create a Query per rating, amend the item list by another randomly assigned
  un-rated item;

- ...

There is no perfect way to create the Query, as it is an approximation afterall.
You should use your discretion in choosing the most appropriate method. In this
tutorial, we go with the first one, i.e. we create a Query per active user with
all active items.

For the Actual Result, we store all raw ratings for that user. It is up to the
evaluator to decide which is good and which is bad.

### Evaluator

We have defined the three main pieces: Query, Predicted Result, and Actual
Result. It remains to compute the quality of an engine instance based on these
data.

We want to tell if a Prediction is good, we achieve this by comparing it with
the Actual Result. But still, Actual Result is just a rating, in this case it is
an integer inclusively between 1 and 5. Suppose the item list in the input Query
is `[a,b,c,d,e]`, the output Predicted Result is `[d,e,c,a,b]` (items at the
front should have better ratings than those at the end), and the user has given
item `a` a rating of 3, item `d` a rating of 5, item `e` a rating of 2, and no
rating for items c and b. Is this Prediction a good one? And what about another
Predicted Result `[d,c,e,a,b]`?

#### Define Good Items

A simple way to define good items is by binary classification. We can simply
assign a *good threshold* to the rating, i.e. items rated at least the threshold
are good. In our evaluator, you can specify this threshold in the
parameter:

```scala
DetailedEvaluatorParams(
  goodThreshold = 3,
  ...)
```

Therefore, in our example, items `a` and `d` are good items, and items `e` is
bad. A perfect ItemRank engine should rank `a` and `d` to the top, and `e` to
the bottom.

> There is a problem with binary classification. Items with rating of 3 is
> treated equally as items with rating of 5. It is possible to use a more
> granular rating, but let's keep it simple in this tutorial.

#### Define Good Ordering

It is worth a considerable amount of effort to define a *good* ordering.
Simply put, is there a difference between `[d,c,a,b,e]` and `[c,d,a,b,e]`?
We know that `d` is a good item, but we have no idea about `c` (i.e. user didn't
rate `c`). One may argue that `[d,c,a,b,e]` is better since it puts a *good*
item before an uncertain one; one may argue otherwise that, suppose MLC's
landing page display 3 items at once, each occupying same amount of space, the
ordering *among* the top 3 items doesn't matter.

It goes back to the very beginning of our discussion, the evaluator
should be closely related to the actual business objective. In the MLC case, it
shows 10 movies at the top of the page, and we don't care about the ordering
among these 10 items.
We can use the
[*Precision@k*](http://en.wikipedia.org/wiki/Precision_and_recall#Precision)
measure (with k = 10), in layman term, the engine is good if it is able to rank
more `good` items to the top 10 result:

```scala
DetailedEvaluatorParams(
  ...,
  measureType = MeasureType.PrecisionAtK,
  measureK = 10)
```

> In the other case where the ordering among the top items matters, one may
> consider using
> [Mean-Average-Precision@k](http://en.wikipedia.org/wiki/Information_retrieval#Mean_average_precision).

### First Evaluation

We are ready to run the actual evaluation. You can find the code at
`examples/scala-local-movielens-evaluation/src/main/scala/Evaluation.scala`.

This tutorial uses app_id = 9, make sure you have imported MovieLens data with
app_id = 9. See
[instructions](../../tutorials/engines/itemrec/movielens.html). Let's ignore
the engine parameters and focus on the evaluator parameters, we will discuss
parameter tuning in the next tutorial.

We use `ItemRankDetailedEvaluator` for evaluation, it takes
`DetailedEvaluatorParams` as parameter. The following code (can be found in
`Evaluation1`) illustrate a complete
parameter set: we use only "rate" action for rating, and consider only good
rating (3 or above); we use Precision@k as our main measure, and set k = 10.

```scala
val evaluatorParams = new DetailedEvaluatorParams(
  actionsMap = Map("rate" -> None),
  goodThreshold = 3,
  measureType = MeasureType.PrecisionAtK,
  measureK = 10
)
```

You can run the evaluation with the following command. This requires a
[standalone spark cluster](http://spark.apache.org/docs/latest/spark-standalone.html)
up and running.

```
$ cd $PIO_HOME/examples/scala-local-movielens-evaluation
$ $PIO_HOME/bin/pio run org.apache.predictionio.examples.mlc.Evaluation1 -- \
  --master spark://`hostname`:7077
...
2014-10-07 19:09:47,136 INFO  workflow.CoreWorkflow$ - Saved engine instance with ID: nfUVwwgMQOqgpb5QEUdAGg
```

If it runs successfully, you should see the workflow ends with something like "Saved engine
instance with ID: nfUVwwgMQOqgpb5QEUdAGg"

To see the result, you can Prediction.IO dashboard:

```
$ $PIO_HOME/bin/pio dashboard
```

By default, you will see it on [http://localhost:9000]. The dashboard displays
all the completed evaluation, for each evaluation, you can also click on the
blue button on the right to see a detailed view of the evaluation.

The value of Precision@10 is 0.0805, (loosely) meaning that for items among the
top 10 results, each has a ~8% chance that the user likes it (i.e. rated >=
3). From the dashboard, we can further drill down the evaluation results.

### Second Evaluation

We have done a small scale evaluation over 3 periods.
The MovieLens dataset provides rating data until mid April 1998, we can extend
the evaluation to 12 periods, each period last 1 week. `Evaluation2` perform
this task. We can run it with the following command:

```
$ $PIO_HOME/bin/pio run org.apache.predictionio.examples.mlc.Evaluation2 -- \
  --master spark://`hostname`:7077
```

The Precision@k is around 0.0673.

### Exercise

Try to play around with different evaluator parameter setting. Consider the
following situation, try to change the parameters set and see what we will get.

- MLC displays 100 items on its first page, what parameter should we change?

- MLC attracts a lot of spontaneous users, they are very active but also get
  bored easily, they usually leave the service in around 10 days, how should we
  change the parameter set to reflect this change?

- MLC is a perfectionist, it wants to focus on the best user experience, it only
  want to suggest the best movies for users, is it possible to change the
  parameter set to address this?

- MLC launches a new mobile set, due to the screen size limitation, it can only
  list the movie recommendation from top to bottom (in contrast to the desktop
  mode where multiple movies are listed horiztonally). How can the evaluation
  parameter address this issue?
