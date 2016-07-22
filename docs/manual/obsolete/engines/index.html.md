---
title: Engines
---


# Engines

<code>This doc is applicable to 0.8.0 only. Updated version for 0.8.2 will be availble soon.</code>

An engine represents a type of prediction. Some examples of engines are Item
Recommendation, Item Ranking, Churn Analysis.

## Getting Started with Engine Instance

Let say you want to deploy an instance of Item Recommendation Engine for product
recommendation. First of all, you will need to create an engine instance project
based on the default Item Recommendation Engine. The new project will contain
configuration for your engine instance.

```
$ $PIO_HOME/bin/pio instance org.apache.predictionio.engines.itemrec
$ cd org.apache.predictionio.engines.itemrec
$ $PIO_HOME/bin/pio register
```
where `$PIO_HOME` is your installation path of PredictionIO.


### Specific the Target App

Inside the engine instance project, edit `params/datasource.json` and change the
value of `appId` to fit your app.

```
{
  "appId": 1,
  "actions": [
    "view",
    "like",
    "conversion",
    ...
```

### Deploying an Engine Instance

```
$ $PIO_HOME/bin/pio train
...
2014-09-11 16:25:44,591 INFO  spark.SparkContext - Job finished: collect at Workflow.scala:674, took 0.078664 s
2014-09-11 16:25:44,737 INFO  workflow.CoreWorkflow$ - Saved engine instance with ID: KxOsC2FRSdGGe1lv0oaHiw
```
where `$PIO_HOME` is your installation path of PredictionIO.

If your training was successful, you should see the lines shown above. Now you are ready to deploy the instance:

```
$ $PIO_HOME/bin/pio deploy
...
[INFO] [09/11/2014 16:26:16.525] [pio-server-akka.actor.default-dispatcher-2] [akka://pio-server/user/IO-HTTP/listener-0] Bound to localhost/127.0.0.1:8000
[INFO] [09/11/2014 16:26:16.526] [pio-server-akka.actor.default-dispatcher-5] [akka://pio-server/user/master] Bind successful. Ready to serve.
```

> The `deploy` command runs the engine instance in the foreground. To run more
than one engine instance, either launch a new console, or put the process into
the background, then repeat the same command on a different port (by adding a
`--port` argument).

By default, the engine instance is bound to localhost, which serves only local traffic.
To serve global traffic, you can use 0.0.0.0, i.e.
`$ bin/pio deploy --ip 0.0.0.0`

If it is your first time using PredictionIO, these [tutorials and
samples](/tutorials/engines) should be helpful.


## Schedule Model Re-training

You may set up a crontab in Linux to update the predictive model with new data
regularly. For example, to run the re-training every 6 hours:

```
$ crontab -e

0 */6 * * *     cd <engine instance project directory>; $PIO_HOME/bin/pio train; $PIO_HOME/bin/pio deploy
```
where `$PIO_HOME` is your installation path of PredictionIO.

> The `deploy` command will automatically undeploy any running engine instance
on the same IP address and port. It is not necessary to use the `undeploy`
command.

## Built-in Engines

PredictionIO comes with the following engines.

* [Item Ranking](/engines/itemrank)
* [Item Recommendation](/engines/itemrec)
* [Item Similarity](/engines/itemsim)

You may start with these [tutorials and samples](/tutorials/engines).

## Building your own Engine

Please read the [Engine Builders' Guide](/enginebuilders/) for details.
