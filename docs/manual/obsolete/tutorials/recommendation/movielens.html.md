---
title: Tutorial on Item Recommendation Engine - Movie Recommendation
---

# Building Movie Recommendation App with Item Recommendation Engine
<code>This doc is applicable to 0.8.0 only. Updated version for 0.8.2 will be availble soon.</code>

## Importing Movie-Lens Data

Clone our
[Python-SDK](https://github.com/PredictionIO/PredictionIO-Python-SDK) and
switch to develop branch to get the latest changes.

```
$ git clone https://github.com/PredictionIO/PredictionIO-Python-SDK.git
$ git checkout develop
```

Install external dependencies.

```
python setup.py install
````

Download Movie-Lens data into the /PredictionIO-Python-SDK folder.

```
$ curl -o ml-100k.zip http://files.grouplens.org/datasets/movielens/ml-100k.zip
$ unzip ml-100k.zip
```

Launch EventServer. $PIO_HOME is the installation directory of PredictionIO.

```
$ $PIO_HOME/bin/pio eventserver
```

Import data. You should have at least 2GB of driver memory to accommodate the dataset. The import script takes two parameters: `<app_id> <url>`.
`<app_id>` is an integer identifies your address space; `<url>` is the
EventServer url (default: http://localhost:7070). We will use the same
`<app_id>` through out this tutorial.

```
$ cd PredictionIO-Python-SDK
$ python -m examples.demo-movielens.batch_import <app_id> http://localhost:7070
```

The import takes a minute or two. At the end you should see the following
output:

```
{u'status': u'alive'}
[Info] Initializing users...
[Info] 943 users were initialized.
...
[Info] Importing rate actions to PredictionIO...
[Info] 100000 rate actions were imported.
```

> You may delete *all* data belonging to a specific `<app_id>` with this
> request.  There is no way to undo this delete, use it cautiously!
```
$ curl -i -X DELETE http://localhost:7070/events.json?appId=<app_id>
```

## Deploying the Item Recommendation engine
Create an engine instance project base on the default Item Recommendation
Engine.

```
$ $PIO_HOME/bin/pio instance org.apache.predictionio.engines.itemrec
$ cd org.apache.predictionio.engines.itemrec
$ $PIO_HOME/bin/pio register
```
where `$PIO_HOME` is your installation path of PredictionIO.
Under the directory `org.apache.predictionio.engines.itemrec`, you will see a
self-contained set of configuation files for an instance of Item Recommendation
Engine.

### Specify the Target App

PredictionIO uses `<app_id>` to distinguish data between different applications.
Engines usually use data from one application. Inside the engine instance project,
the file `params/datasource.json` defines how data are read from the Event Server.
Change the value of `appId` to `<app_id>` which you used for importing.

```json
{
  "appId": <app_id>,
  "actions": [
    "view",
    "like",
    ...
  ],
  ...
}
```

### Train and deploy

Call `pio train` to kick start training.

```
$ $PIO_HOME/bin/pio train
...
2014-09-20 01:17:39,997 INFO  spark.ContextCleaner - Cleaned broadcast 9
2014-09-20 01:17:40,194 INFO  workflow.CoreWorkflow$ - Saved engine instance with ID: RWTien4GSeCrl3fpZwhJDA
```

Once the training is successful, you can deploy the instance. It will start a
Engine Server at `http://localhost:8000`, you can change to another port by
specifying `--port <port>`.

```
$ $PIO_HOME/bin/pio deploy
...
[INFO] [09/20/2014 01:19:45.428] [pio-server-akka.actor.default-dispatcher-3] [akka://pio-server/user/IO-HTTP/listener-0] Bound to localhost/127.0.0.1:8000
[INFO] [09/20/2014 01:19:45.429] [pio-server-akka.actor.default-dispatcher-5] [akka://pio-server/user/master] Bind successful. Ready to serve.
```

Another way of making sure an Engine Server is deployed successfully is by
visiting its status page [http://localhost:8000]. You will see information
associated with engine instance like when it is started, trained, its component
classes and parameters.

### Retrieving Prediction Results
With the EngineClients of a PredictionIO SDK, your application can send queries
to a deployed engine instance through the Engine API.

To get 3 personalized item recommendations for user "100".

<div class="tabs">
  <div data-tab="Raw HTTP" data-lang="bash">
<p>Line breaks are added for illustration in the response.</p>

```bash
$ curl -i -X POST -d '{"uid": "100", "n": 3}' http://localhost:8000/queries.json
{"items":[
  {"272":9.929327011108398},
  {"313":9.92607593536377},
  {"347":9.92170524597168}]}
```
  </div>
  <div data-tab="Python SDK" data-lang="python">
```python
from predictionio import EngineClient
client = EngineClient(url="http://localhost:8000")
prediction = client.send_query({"uid": "100", "n": 3})
print prediction
```

<p>Output:</p>
```bash
{u'items': [{u'272': 9.929327011108398}, {u'313': 9.92607593536377}, {u'347':
9.92170524597168}]}
```
  </div>
</div>
