---
title:  Item Ranking Engine | Built-in Engines
---

# Item Ranking Engine: Overview

**Rank a list of items to a user personally**

With this engine, you can personalize a ranked list of items in your
application. The engine rank items in two steps:

## Step 1: Predict User Preferences

![Item Ranking Score Prediction](/images/engine-itemrec-prediction.png)

In this batch-mode process, the engine predicts preference scores for the
queried items. The scores are computed by the deployed algorithm in the engine.

## Step 2: Rank the Query Items

With the predicted scores, this engine can rank a list of items for the user
according to queries. Ranked items with scores will then be returned. Original
order of the items is preserved if the algorithm couldn't predict the score.

# Collect Events Data

You may collect events data with HTTP request or by using one of many
**PredictionIO SDKs**.

## 1. Setting user entity

The `eventTime` is the time of the user being created in your application (eg.
when she registered or signed up the account).

If you need to update the properties of the existing user entity later, simply
create another `$set` event for this user entity with new property values and
the `eventTime` is the time of this change happened.

To create an user with `id_1`:

<div class="tabs">
  <div data-tab="Raw HTTP" data-lang="bash">
```bash
$ curl -i -X POST http://localhost:7070/events.json \
-H "Content-Type: application/json" \
-d '{
  "appId" : 1,
  "event" : "$set",
  "entityType" : "user",
  "entityId" : "id_1",
  "eventTime" : "2004-12-13T21:39:45.618-07:00"
}'
```
  </div>
  <div data-tab="PHP SDK" data-lang="php">
```php
<?php
use predictionio\EventClient;

$appId = 1;
$client = new EventClient($appId);
$client->setUser('id_1', array(), '2004-12-13T21:39:45.618-07:00');
?>
```
  </div>
  <div data-tab="Python SDK" data-lang="python">
```python
from predictionio import EventClient
from datetime import datetime
import pytz

event_client = EventClient(app_id=1, url="http://localhost:7070")

tzinfo = pytz.timezone('US/Mountain')
event_time = datetime(2014, 12, 13, 21, 39, 45, 618, tzinfo=tzinfo)
event_client.set_user(uid="id_1", event_time=event_time)
```
  </div>
  <div data-tab="Ruby SDK" data-lang="ruby">
```ruby
require 'predictionio'

event_client = PredictionIO::EventClient.new(1)
event_client.set_user('id_1',
                      'eventTime' => '2004-12-13T21:39:45.618-07:00')
```
  </div>
  <div data-tab="Java SDK" data-lang="java">
```java
import com.google.common.collect.ImmutableMap;
import org.apache.predictionio.EventClient;
import org.joda.time.DateTime;

EventClient eventClient = new EventClient(1);
eventClient.setUser("id_1", ImmutableMap.<String, Object>of(), new DateTime("2004-12-13T21:39:45.618-07:00"));
```
  </div>
</div>


## 2. Setting item entity

The `eventTime` is the time of the item being first created in your application.
The property `pio_itypes` is required when you set the item for the first time.

If you need to update the properties of the existing item entity, simply create
another `$set` event for this item entity with new properties values and the
`eventTime` is the time of this change happened.

To create an item with `id_3` and set its `pio_itypes` to `"type1"`:

<div class="tabs">
  <div data-tab="Raw HTTP" data-lang="bash">
```bash
$ curl -i -X POST http://localhost:7070/events.json \
-H "Content-Type: application/json" \
-d '{
  "appId" : 1,
  "event" : "$set",
  "entityType" : "pio_item",
  "entityId" : "id_3",
  "properties" : {
    "pio_itypes" : ["type1"]
  },
  "eventTime" : "2004-12-13T21:39:45.618-07:00"
}'
```
  </div>
  <div data-tab="PHP SDK" data-lang="php">
```php
<?php
$client->setItem('id_3',
           array('pio_itypes'=>array('type1')),
           '2004-12-13T21:39:45.618-07:00'
         );
?>
```
  </div>
  <div data-tab="Python SDK" data-lang="python">
```python
event_client.set_item(
    iid="id_3",
    properties={"pio_itypes": ["type1"]},
    event_time=event_time)
```
  </div>
  <div data-tab="Ruby SDK" data-lang="ruby">
```ruby
event_client.set_item('id_3',
                      'eventTime' => '2004-12-13T21:39:45.618-07:00',
                      'properties' => { 'pio_itypes' => %w(type1) })
```
  </div>
  <div data-tab="Java SDK" data-lang="java">
```java
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.predictionio.EventClient;
import java.util.Map;
import org.joda.time.DateTime;

Map properties = ImmutableMap.<String, Object>of(
        "pio_types", ImmutableList.of("type1"));
eventClient.setItem("id_3", properties, new DateTime("2004-12-13T21:39:45.618-07:00"));
```
  </div>
</div>

## 3. Record events of user-to-item actions.

The `eventTime` is the time of the action being performed by the user. the
`event` is the action name. By default, the built-in engines support the
following actions: `"view"`, `"like"`, `"rate"`, `"conversion"` and `"dislike"`.
You may also use other custom actions by modifying the params file
`datasource.json` and `preparator.json` (Please refer to the later section
below.)

To record that user `id_1` views the item `id_3`:

<div class="tabs">
  <div data-tab="Raw HTTP" data-lang="bash">
```bash
$ curl -i -X POST http://localhost:7070/events.json \
-H "Content-Type: application/json" \
-d '{
  "appId" : 1,
  "event" : "view",
  "entityType" : "pio_user"
  "entityId" : "id_1",
  "targetEntityType" : "pio_item",
  "targetEntityId": "id_3",
  "eventTime" : "2012-01-20T20:33:41.452-07:00"
}'
```
  </div>
  <div data-tab="PHP SDK" data-lang="php">
```php
<?php
$client->recordUserActionOnItem('view',
                       'id_1', 'id_3',
                       array(),
                       '2012-01-20T20:33:41.452-07:00'
         );
?>
```
  </div>
  <div data-tab="Python SDK" data-lang="python">
```phython
event_client.record_user_action_on_item(
    action="view",
    uid="id_1",
    iid="id_3",
    event_time=datetime(2012, 1, 20, 20, 33, 41, 452, tzinfo=tzinfo))
```
  </div>
  <div data-tab="Ruby SDK" data-lang="ruby">
```ruby
event_client.record_user_action_on_item('view', 'id_1', 'id_3',
                                        'eventTime' =>
                                          '2012-01-20T20:33:41.452-07:00')
```
  </div>
  <div data-tab="Java SDK" data-lang="java">
```java
import com.google.common.collect.ImmutableMap;
import org.apache.predictionio.EventClient;
import org.joda.time.DateTime;

eventClient.userActionItem("view", "id_1", "id_3", ImmutableMap.<String, Object>of(),
    new DateTime("2004-12-13T21:39:45.618-07:00"));
```
  </div>
</div>

Optionally, you can specify the `pio_rating` property associate with this event
of user-to-item action.

To record that user `id_1` rates the item `id_3` with rating of 4:

<div class="tabs">
  <div data-tab="Raw HTTP" data-lang="bash">
```bash
$ curl -i -X POST http://localhost:7070/events.json \
-H "Content-Type: application/json" \
-d '{
  "appId" : 1,
  "event" : "view",
  "entityType" : "pio_user"
  "entityId" : "id_1",
  "targetEntityType" : "pio_item",
  "targetEntityId": "id_3",
  "properties" : {
    "pio_rating" : 4
  }
  "eventTime" : "2012-01-20T20:33:41.452-07:00"
}'
```
  </div>
  <div data-tab="PHP SDK" data-lang="php">
```php
<?php
$client->recordUserActionOnItem('view',
                       'id_1', 'id_3',
                       array('pio_rating'=>4),
                       '2012-01-20T20:33:41.452-07:00'
         );
?>
```
  </div>
  <div data-tab="Python SDK" data-lang="python">
```python
event_client.record_user_action_on_item(
    action="view",
    uid="id_1",
    iid="id_3",
    properties={"pio_rating": 4},
    event_time=datetime(2012, 1, 20, 20, 33, 41, 452, tzinfo=tzinfo))  
```
  </div>
  <div data-tab="Ruby SDK" data-lang="ruby">
```ruby
event_client.record_user_action_on_item('view', 'id_1', 'id_3',
                                        'eventTime' =>
                                          '2012-01-20T20:33:41.452-07:00',
                                        'properties' => { 'pio_rating' => 4 })
```
  </div>
  <div data-tab="Java SDK" data-lang="java">
```java
import com.google.common.collect.ImmutableMap;
import org.apache.predictionio.EventClient;
import java.util.Map;
import org.joda.time.DateTime;

Map properties = ImmutableMap.<String, Object>("pio_rating", 4);
eventClient.userActionItem("view", "id_1", "id_3", properties,
        new DateTime("2012-01-20T20:33:41.452-07:00"));
```
  </div>
</div>


# Events Data Requirement

This built-in engine requires the following Events data:

Your Events data should involve two EntityTypes:

1. `pio_user` Entity: the user of your application
2. `pio_item` Entity: the item of your application with the following
   properties:
  - `pio_itypes`: array of String. Array of itypes. Each item should have at
    least one itype. The item may have multiple itypes.
  - `pio_starttime`: (Optional) ISO 8601 timestamp. Start time that the item
    becomes available.

Events between these two Entity Types should be recorded:

- user-to-item action Events: such as like, rate and view, with following
  properties:
  - `pio_rating`: (Optional) integer rating


> **Note: Name of EntityType and Properties**
>
> Although you are allowed to use different names for the Entity Type and
> Properties as long as they represent same meaning (For example, use the name
> `user` instead of `pio_user` or use `t` instead of `pio_starttime`). We highly
> recommend to follow our name convention when using built-in engines. If you
> use diffrent names for these attributes, you need to modify the
> `attributeNames` field defined in the file `datasource.json`.

> **Note: Extra User and Item Entity Properties**
>
> Your user data may contain additional properties, such as age and gender. Your
> item data may also contain other properties, such as price and title. What
> kind of properties you need to provide depends on the algorithm you choose to
> build the model.
>
> Currently, all built-in algorithms in PredictionIO are Collaborative Filtering
> (CF) algorithms. CF algorithms derive the feature vectors of users and items
> from previous behaviors, i.e. score, only. Therefore, you simply need to
> identify each user and item with a unique ID. No extra data properties are
> needed.
>
> It does not mean that CF algorithms are less accurate though. In fact,
> researches (such as this) show the exact opposite. An algorithm that requires
> no data attribute can be the winning algorithm.

# Data Source

The engine comes with a Data Source which read the events data from the
datastore for processing.

You need to modify `appId` in the params file `datasource.json` to your appId.

# Data Preparator

The engine comes with a Data Preparator to parpare data for the built-in
algorithims. It has the following parameters:

Field | Type | Description
:---- | :----| :------
`actions` | Map | Configure how to map the user-to-item action to a rating score. Specify `null` if use value of `pio_rating` of the corresponding action.
`conflict`| String | Specify how to resolve the conflict if the user has multiple actions on the same item. Supported values: "latest", "highest", "lowest", "sum".
  |  | "latest" - Use the latest action (default)
  |  | "highest" - Use the highest score one
  |  | "lowest" - Use the lowest score one
  |  | "sum" - Sum all action scores.

You can change the default setting by modifying the params file `preparator.json`.

# Item Ranking Engine API

Item Ranking Engine supports the following query API endpoints:

## Sending Queries to Item Ranking Engine

To rank a list of items for a user, make an HTTP POST request to the Item Ranking Engine instance:

**POST** *Engine_Instance_IP*:*Engine_Instance_port*


with following JSON payload:

Field | Description
------ | :---------
`uid` | user Entity ID
`iids`| array of item Entity ID

#### Sample Query

To personalize the order of items of "1", "3", "5", "10" and "11" for user "123":

<div class="tabs">
  <div data-tab="Raw HTTP" data-lang="bash">
```bash
$ curl -i -X POST http://localhost:8000/queries.json \
-d '{
  "uid" : "123",
  "iids" : ["1", "3", "5", "10", "11"]
}'
```
  </div>
  <div data-tab="PHP SDK" data-lang="php">
```php
<?php
use predictionio\EngineClient;

$engineClient = new EngineClient('http://localhost:8000');
$predictions = $engineClient->sendQuery(
                      array(
                        'uid'=>'123',
                        'iids'=>array('1', '3', '5', '10', '11')
                      )
               );
print_r($predictions);
?>
```
  </div>
  <div data-tab="Python SDK" data-lang="python">
```python
from predictionio import EngineClient
engine_client = EngineClient(url="http://localhost:8000")

prediction = engine_client.send_query(
    data={"uid": "123", "iids": ["1", "3", "5", "10", "11"]})
print(prediction)
```
  </div>
  <div data-tab="Ruby SDK" data-lang="ruby">
```ruby
require 'predictionio'

client = PredictionIO::EngineClient.new

predictions = client.send_query('uid' => '123', 'iids' => %w(1 3 5 10 11))
puts predictions
```
  </div>
  <div data-tab="Java SDK" data-lang="java">
```java
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.predictionio.EngineClient;
import org.joda.time.DateTime;

EngineClient engineClient = new EngineClient(apiURL);
engineClient.sendQuery(ImmutableMap.<String, Object>of(
        "uids", "123",
        "iids", ImmutableList.of("1", "3", "5", "10", "11")
    ));
```
  </div>
</div>


The API returns the following JSON response:

Field | Description
:---- | :----------
`items` | array of { item Entity ID : predicted preference score }
        | in ranked order
`isOriginal`| if set to true, the items are not ranked
            | (because the algorithm cannot predict)

#### Sample Response

```json
{"items":[{"10":35.15679457387672},{"11":14.929159003452385},{"1":6.950646135607128},{"3":6.894567600916194},{"5":3.9688094914951138}],"isOriginal":false}
```

# Algorithms

## Changing Algorithm and Its Parameters

By default, **Mahout Item Based Algorithm** (`"mahoutItemBased"`) is used. You
can switch to another algorithm or modify parameters by modifying the file
`algorithms.json` with any of above algorithm's JSON parameters setting.

Please read [Selecting an
Algorithm](/cookbook/choosingalgorithms.html) for tips on
selecting the right algorithm and setting the parameters properly.

> You may also [implement and add your own
algorithm](/cookbook/addalgorithm.html) to the engine easily.

Item Ranking Engine comes with the following algorithms:

## 1. Mahout Item Based Algorithm

Use Mahout Item Based algorithm to build similarity matrix. Then rank items
based on user recent history and the item similarity matrix.

**Algorithm code name:** `"mahoutItemBased"`

**Parameters:**

Field | Type | Description
:---- | :----| :------
`booleanData` | boolean | Treat input data as having no preference values.
`itemSimilarity`| String | Item Similarity Measure. See **Note**
`weighted` | boolean | The Similarity score is weighted (only applied to Euclidean Distance, Pearson Correlation, Uncentered Cosine item similarity).
`nearestN` | integer | K-nearest rated item neighbors,
`threshold` | double | Similarity threshold. Discard item pairs with a similarity value below this.
`numSimilarItems` | integer | Number of similar items in the Item Similarity Matrix model.
`numUserActions`| integer | Number of user-to-item actions in the user history model.
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
    "name": "mahoutItemBased",
    "params": {
      "booleanData": true,
      "itemSimilarity": "LogLikelihoodSimilarity",
      "weighted": false,
      "nearestN": 10,
      "threshold": 4.9E-324,
      "numSimilarItems": 50,
      "numUserActions": 50,
      "freshness" : 0,
      "freshnessTimeUnit" : 86400
    }
  }
]
```

## 2. Feature Based Algorithm

Rank items based on item's feature vector (`pio_itypes`).

**Algorithm code name:** `"featurebased"`

**Parameters:**

This algorithm doesn't have parameters.

**Default algorithm parameters:**

```json
[
  {
    "name": "featurebased",
    "params": {}
  }
]

```

## 3. Random Algorithm

Rank items randomly (mainly for baseline evaluation purpose).

**Algorithm code name:** `"rand"`

**Parameters:**

This algorithm doesn't have parameters.

**Default algorithm parameters:**

```json
[
  {
    "name": "rand",
    "params": {}
  }
]

```

## 4. Non-cached Mahout Item Based Algorithm

Use Mahout Item Based algorithm to re-calculate predicted score every time when
serve the query request. The item similarity matrix is not cached. (Serving
performance is slower)

**Algorithm code name:** `"ncMahoutItemBased"`

**Parameters:**

Same as **Mahout Item Based Algorithm** *without* the following parameters:

- `numSimilarItems`
- `numUserActions`


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
      "freshness" : 0,
      "freshnessTimeUnit" : 86400
    }
  }
]
```

# Using Custom Actions

By default, the built-in engines support the following actions: `"view"`,
`"like"`, `"dislike"`, `"rate"` and `"conversion"`. To add your own custom
actions, you need to modify the following params file:

*   `datasource.json`: Add your custom action names into the parameters
    `actions` and `u2iActions`.

*   `preparator.json`: Define how to map your custom action names to a score in
    the parameters `actions`. Use value of `null` if your action has a
    `pio_rating` property.
