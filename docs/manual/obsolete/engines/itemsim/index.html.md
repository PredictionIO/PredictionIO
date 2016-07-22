---
title:  Item Similarity Engine | Built-in Engines
---

# ITEM SIMILARITY ENGINE: OVERVIEW

**People who like this may also like....**

This engine tries to suggest top **N** items that are similar to one of more targeted items. By being 'similar', it does not necessarily mean that the two items look alike, nor they share similar attributes. The definition of similarity is independently defined by each algorithm and is usually calculated by a distance function. The built-in algorithms assume that similarity between two items means the likelihood any user would like (or buy, view etc) both of them.

The engine suggests similar items through a two-step process:


## Step 1: Estimate Item Similarity

![Item Sim Score Prediction](/images/engine-itemsim-score.png)

In this batch-mode process, the engine estimates a similarity score for every item-item pair. The scores are computed by the deployed algorithm in the engine.

## Step 2: Rank Top N Items

With the similarity scores, this engine can rank all available items according to their similarity to the targeted items. Top N most similar items will then be returned.


# Collect Events Data / Events Data Requirement
The process of collecting events data for the Item Similarity Engine is
equivalent to that of Item Ranking Engine. Please refer to [Item Ranking Engine
/ Collect Events Data](/engines/itemrank) for detailed explanation.

# Data Source
Same as [Item Ranking Engine / Data Source](/engines/itemrank).

# Data Preparator
Same as [Item Ranking Engine / Data Preparator](/engines/itemrank).

# Item Similarity Engine API

Item Similarity Engine supports the following API endpoints:

## Sending Queries to Item Similarity Engine

To suggest top N items that are most similar to one or more targeted items, make an HTTP POST request to the Item Similarity Engine instance:

**POST** `<engine_instance_url>/queries.json`

with following JSON payload:

Field | Description
------ | :---------
`iids` | Array of item entity IDs.
`n` | maximum number of items recommended

> *Note*
>
>If multiple item IDs are specified (For example: `"iids": ["item0", "item1", "item2"]`, all the specified items will be taken into account when return the top N similar items. One typical usage is that you could keep track a list of recent viewed items of the user and then use this list of recently viewed items to recommend items to the user. This could also be used to provide recommendation to anonymous users as soon as they have viewed a few items.

#### Sample Query

To get top 5 items which are similar to items "12", "1", "19":

<div class="tabs">
  <div data-tab="Raw HTTP" data-lang="bash">
```bash
$ curl -X POST http://localhost:9997/queries.json \
-d '{"iids": ["12", "1", "19"], "n": 5}'
```
  </div>
  <div data-tab="PHP SDK" data-lang="php">
```php
<?php
use predictionio\EngineClient;

$engineClient = new EngineClient('http://localhost:9997');
$predictions = $engineClient->sendQuery(
                      array(
                        'iids'=>array('12', '1', '19'),
                        'n'=>5
                      )
               );
print_r($predictions);
?>
```
  </div>
  <div data-tab="Python SDK" data-lang="python">
```python
from predictionio import EngineClient
engine_client = EngineClient(url="http://localhost:9997")

prediction = engine_client.send_query(data={"iids": ["12", "1", "19"], "n" : 5})
print(prediction)
```
  </div>
  <div data-tab="Ruby SDK" data-lang="ruby">
```ruby
require 'predictionio'

client = PredictionIO::EngineClient.new('http://localhost:9997')

predictions = client.send_query('iids' => %w(12 1 19), 'n' => 5)
puts predictions
```
  </div>
  <div data-tab="Java SDK" data-lang="java">
```java
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.predictionio.EngineClient;

EngineClient engineClient = new EngineClient("http://localhost:9997");
engineClient.sendQuery(ImmutableMap.<String, Object>of(
        "iids", ImmutableList.of("12", "1", "19"),
        "n", 5));
```
  </div>
</div>

The API returns the following JSON response:

Field | Description
:---- | :----------
`items` | array of { item Entity ID : predicted preference score }
        | in descending order.

#### Sample Response

```json
{"items":[{"17":0.8666961789131165},{"41":0.793708860874176},{"42":0.7885419726371765},{"36":0.7584964036941528},{"2":0.7584964036941528}]}
```


# Algorithms

## Changing Algorithm and Its Parameters

By default, **Non-cached Mahout Item Based Algorithm** (`"ncMahoutItemBased"`)
is used. You can switch to another algorithm or modify parameters by modifying
the file `algorithms.json` with any of above algorithm's JSON parameters
setting.

Please read [Selecting an
Algorithm](/cookbook/choosingalgorithms.html) for tips on
selecting the right algorithm and setting the parameters properly.

INFO: You may also [implement and add your own
algorithm](/cookbook/addalgorithm.html) to the engine easily.

Item Similarity Engine comes with the following algorithms:

## 1. Non-cached Mahout Item Based Algorithm

Use Mahout Item Based algorithm to calculate similarity scoree when serve
the query request. The item similarity matrix is not cached.

**Algorithm code name:** `"ncMahoutItemBased"`

**Parameters:**

Field | Type | Description
:---- | :----| :------
`booleanData` | boolean | Treat input data as having no preference values.
`itemSimilarity`| String | Item Similarity Measure. See **Note**
`weighted` | boolean | The Similarity score is weighted (only applied to Euclidean Distance, Pearson Correlation, Uncentered Cosine item similarity).
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
      "freshness" : 0,
      "freshnessTimeUnit" : 86400
    }
  }
]
```


# Using Custom Actions

By default, the built-in engine parameters support the following actions:
`"view"`, `"like"`, `"dislike"`, `"rate"` and `"conversion"`.
To add your own custom actions, you could simply modify the following params file:

*   `datasource.json`: Add your custom action names into the parameters
    `actions` and `u2iActions`.

*   `preparator.json`: Define how to map your custom action names to a score in
    the parameters `actions`. Use value of `null` if your action has a
    `pio_rating` property.
