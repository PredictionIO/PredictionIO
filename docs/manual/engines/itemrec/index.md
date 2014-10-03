---
layout: docs
title:  Item Recommendation Engine | Built-in Engines
---

# Item Recommendation Engine: Overview

**Recommend N items to a user personally**

With this engine, you can add discovery or recommendation features to your
application. The engine makes recommendation in two steps:

## Step 1: Predict User Preferences

![Item Recommendation Score
Prediction]({{site.baseurl}}/images/engine-itemrec-prediction.png)

In this batch-mode process, the engine predicts a preference score for every
user-item pair. The scores are computed by the deployed algorithm in the engine.

<!---
## Step 2: Rank the Query Items

With the predicted scores, this engine can rank all available items for any user
according to your REST API/SDK queries. Advanced queries, such as Geo-based
search, is supported. Top N items will then be returned as prediction results.
-->

## Step 2: Return Personalized High Score Items

With the predicted scores, this engine can rank all available items for any user
according to your REST API/SDK queries.
Top N items will then be returned as prediction results.

# Tutorials

Create your first Item Recommendation app quickly by following [tutorials and
samples]({{ site.baseurl }}/tutorials/engines/).


# Collect Events Data / Events Data Requirement
The process of collecting events data for the Item Recommendation Engine is
equivalent to that of Item Ranking Engine. Please refer to [Item Ranking Engine
/ Collect Events Data](../itemrank/) for detailed explanation.

# Data Source
Same as [Item Ranking Engine / Data Source](../itemrank/).

# Data Preparator
Same as [Item Ranking Engine / Data Preparator](../itemrank/).

<!---
# Prediction Query API

Item Recommendation Engine supports the following API endpoints:

## Get Top N Recommendation

To recommend top N items to a user, make an HTTP GET request to itemrec engine
URI:

```
GET /<TODO>
```

The query is a targeted user while the output is a list of N items.
-->

# Item Recommendation Engine API

Item Recommendation Engine supports the following query API endpoints:

## Sending Queries to Item Recommendation Engine

To get a list of recommended items for a user, make an HTTP POST request to the
Item Recommendation Engine instance:

**POST** `<engine_instance_url>`

with following JSON payload:

Field | Description
------ | :---------
`uid` | user Entity ID
`n` | maximum number of items recommended

#### Sample Query

To get a 3 personalized item recommendations for user "1".

<div class="codetabs">
<div data-lang="Raw HTTP">
{% highlight bash %}
$ curl -X POST http://localhost:9993/queries.json \
-d '{"uid": "1", "n": 3}'
{% endhighlight %}
</div>
<div data-lang="PHP SDK">
{% highlight php %}
<?php
use predictionio\EngineClient;

$engineClient = new EngineClient('http://localhost:9993');
$predictions = $engineClient->sendQuery(
                      array(
                        'uid'=>'1',
                        'n'=>3
                      )
               );
print_r($predictions);
?>
{% endhighlight %}
</div>
<div data-lang="Python SDK">
{% highlight python %}
from predictionio import EngineClient
engine_client = EngineClient(url="http://localhost:9993")

prediction = engine_client.send_query(data={"uid": "1", "n" : 3})
print(prediction)
{% endhighlight %}
</div>
<div data-lang="Ruby SDK">
{% highlight ruby %}
require 'predictionio'

client = PredictionIO::EngineClient.new('http://localhost:9993')

predictions = client.send_query('uid' => '1', 'n' => 3)
puts predictions
{% endhighlight %}
</div>
<div data-lang="Java SDK">
{% highlight java %}
import com.google.common.collect.ImmutableMap;
import io.prediction.EngineClient;

EngineClient engineClient = new EngineClient("http://localhost:9993");
engineClient.sendQuery(ImmutableMap.<String, Object>of(
        "uid", "1",
        "n", 3
    ));
{% endhighlight %}
</div>

</div>

#### Sample Response

The API returns the following JSON response:

Field | Description
:---- | :----------
`items` | array of { item Entity ID : predicted preference score }
        | in descending order.

```json
{"items":[{"1":5.9279937744140625},{"19":5.583907127380371},{"2":5.424792289733887}]}
```

# Algorithms

## Changing Algorithm and Its Parameters

By default, **Non-cached Mahout Item Based Algorithm** (`"ncMahoutItemBased"`)
is used. You can switch to another algorithm or modify parameters by modifying
the file `algorithms.json` with any of above algorithm's JSON parameters
setting.

Please read [Selecting an
Algorithm]({{site.baseurl}}/cookbook/choosingalgorithms.html) for tips on
selecting the right algorithm and setting the parameters properly.

> You may also [implement and add your own
algorithm]({{site.baseurl}}/cookbook/addalgorithm.html) to the engine easily.

Item Recommendation Engine comes with the following algorithms:

## 1. Non-cached Mahout Item Based Algorithm

Use Mahout Item Based algorithm to build similarity matrix. Then rank items
based on user recent history and the item similarity matrix.

**Algorithm code name:** `"ncMahoutItemBased"`

**Parameters:**

Field | Type | Description
:---- | :----| :------
`booleanData` | boolean | Treat input data as having no preference values.
`itemSimilarity`| String | Item Similarity Measure. See **Note**
`weighted` | boolean | The Similarity score is weighted (only applied to Euclidean Distance, Pearson Correlation, Uncentered Cosine item similarity).
`nearestN` | integer | K-nearest rated item neighbors,
`unseenOnly` | boolean | Only previously unseen (i.e. unrated) items will be returned.
`threshold` | double | Similarity threshold. Discard item pairs with a similarity value below this.
`freshness` | integer | Freshness scale 0 - 10. Must be >= 0. 0 means no freshness.
`freshnessTimeUnit` | integer | The time unit in seconds for freshness prioritization. As an example, if you set this to one day (86400), and freshness is set to 10, items that are one day old would have their score degraded by a bit more than 60%, or e^-1 remains to be exact.

**Note:**

Supported value for `itemSimilarity`

Name | Value
:---- | :----
City Block | `CityBlockSimilarity`
Euclidean Distance | `EuclideanDistanceSimilarity`
Log-Likelihood | `LogLikelihoodSimilarity`
Pearson Correlation | `PearsonCorrelationSimilarity`
Tanimoto Coefficient | `TanimotoCoefficientSimilarity`
Uncentered Cosine | `UncenteredCosineSimilarity`

**Default algorithm parameters:**

```json
[
  {
    "name": "ncMahoutItemBased",
    "params": {
      "booleanData": true,
      "itemSimilarity": "LogLikelihoodSimilarity",
      "weighted": false,
      "threshold": 4.9E-324,
      "nearestN": 10,
      "unseenOnly": false,
      "freshness" : 0,
      "freshnessTimeUnit" : 86400
    }
  }
]
```

## 2. Feature Based Algorithm
Coming soon.
