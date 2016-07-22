---
title: Upgrade Instructions
---


This page highlights major changes in each version and upgrade tools.

# How to upgrade

To upgrade and use new version of PredictionIO, do the following:

- Download and unzip the new PredictionIO binary (the download path can be found in the [Download PredictionIO section](/install/install-linux/#method-2:-manual-install))
- Retain the setting from current PredictionIO/conf/pio-env.sh to the new PredictionIO/conf/pio-env.sh.
- If you have added PredictionIO/bin to your `PATH` environment variable before, change it to the new PredictionIO/bin as well.

# Additional Notes for Specific Versions Upgrade

In addition, please take notes of the following for specific version upgrade.

## Upgrade to 0.9.2

The Spark dependency has been upgraded to version 1.3.0. All engines must be
rebuilt against it in order to work.

Open and edit `build.sbt` of your engine, and look for these two lines:

```scala
"org.apache.spark" %% "spark-core"    % "1.2.0" % "provided"

"org.apache.spark" %% "spark-mllib"   % "1.2.0" % "provided"
```

Change `1.2.0` to `1.3.0`, and do a clean rebuild by `pio build --clean`. Your
engine should now work with the latest Apache Spark.


### New PEventStore and LEventStore API

In addition, new PEventStore and LEventStore API are introduced so that appName can be used as parameters in engine.json to access Event Store.

NOTE: The following changes are not required for using 0.9.2 but it's recommended to upgrade your engine code as described below because the old API will be deprecated.

#### 1. In **DataSource.scala**:

- remove this line of code:

    ```scala
    import org.apache.predictionio.data.storage.Storage
    ```

    and replace it by

    ```scala
    import org.apache.predictionio.data.store.PEventStore
    ```

- Change `appId: Int` to `appName: String` in DataSourceParams

    For example,

    ```scala
    case class DataSourceParams(appName: String) extends Params
    ```

- remove this line of code: `val eventsDb = Storage.getPEvents()`

- locate where `eventsDb.aggregateProperties()` is used, change it to `PEventStore.aggregateProperties()`:

    For example,

    ```scala

      val usersRDD: RDD[(String, User)] = PEventStore.aggregateProperties( // CHANGED
        appName = dsp.appName, // CHANGED: use appName
        entityType = "user"
      )(sc).map { ... }

    ```

- locate where `eventsDb.find() `is used, change it to `PEventStore.find()`

    For example,

    ```scala

      val viewEventsRDD: RDD[ViewEvent] = PEventStore.find( // CHANGED
        appName = dsp.appName, // CHANGED: use appName
        entityType = Some("user"),
        ...

    ```

#### 2. In **XXXAlgorithm.scala**:

If Storage.getLEvents() is also used in Algorithm (such as ALSAlgorithm of E-Commerce Recommendation template), you also need to do following:

NOTE: If `org.apache.predictionio.data.storage.Storage` is not used at all (such as Recommendation, Similar Product, Classification, Lead Scoring, Product Ranking template), there is no need to change Algorithm and can go to the later **engine.json** section.

- remove `import org.apache.predictionio.data.storage.Storage` and replace it by `import org.apache.predictionio.data.store.LEventStore`
- change `appId` to `appName` in the XXXAlgorithmParams class.
- remove this line of code: `@transient lazy val lEventsDb = Storage.getLEvents()`
- locate where `LEventStore.findByEntity()` is used, change it to `LEventStore.findByEntity()`:

    For example, change following code

    ```scala
      ...
      val seenEvents: Iterator[Event] = lEventsDb.findSingleEntity(
        appId = ap.appId,
        entityType = "user",
        entityId = query.user,
        eventNames = Some(ap.seenEvents),
        targetEntityType = Some(Some("item")),
        // set time limit to avoid super long DB access
        timeout = Duration(200, "millis")
      ) match {
        case Right(x) => x
        case Left(e) => {
          logger.error(s"Error when read seen events: ${e}")
          Iterator[Event]()
        }
      }
    ```

    to

    ```scala
      val seenEvents: Iterator[Event] = try { // CHANGED: try catch block is used
        LEventStore.findByEntity( // CHANGED: new API
          appName = ap.appName, // CHANGED: use appName
          entityType = "user",
          entityId = query.user,
          eventNames = Some(ap.seenEvents),
          targetEntityType = Some(Some("item")),
          // set time limit to avoid super long DB access
          timeout = Duration(200, "millis")
        )
      } catch { // CHANGED: try catch block is used
        case e: scala.concurrent.TimeoutException =>
          logger.error(s"Timeout when read seen events." +
            s" Empty list is used. ${e}")
          Iterator[Event]()
        case e: Exception =>
          logger.error(s"Error when read seen events: ${e}")
          throw e
      }
    ```

    If you are using E-Commerce Recommendation template, please refer to the latest version for other updates related to `LEventStore.findByEntity()`

#### 3. In **engine.json**:

locate where `appId` is used, change it to `appName` and specify the name of the app instead.

For example:

```json
  ...

  "datasource": {
    "params" : {
      "appName": "MyAppName"
    }
  },

```

Note that other components such as `algorithms` may also have `appId` param (e.g. E-Commerce Recommendation template). Remember to change it to `appName` as well.

That's it! You can re-biuld your engine to try it out!

## Upgrade to 0.9.0

0.9.0 has the following new changes:

- The signature of `P2LAlgorithm` and `PAlgorithm`'s `train()` method is changed from

    ```scala
      def train(pd: PD): M
    ```

    to

    ```scala
      def train(sc: SparkContext, pd: PD): M
    ```

    which allows you to access SparkContext inside `train()` with this new parameter `sc`.

- A new SBT build plugin (`pio-build`) is added for engine template


WARNING: If you have existing engine templates running with previous version of PredictionIO, you need to either download the latest templates which are compatible with 0.9.0, or follow the instructions below to modify them.

Follow instructions below to modify existing engine templates to be compatible with PredictionIO 0.9.0:

1. Add a new parameter `sc: SparkContext` in the signature of `train()` method of algorithm in the templates.

    For example, in Recommendation engine template, you will find the following `train()` function in `ALSAlgorithm.scala`

    ```scala
    class ALSAlgorithm(val ap: ALSAlgorithmParams)
      extends P2LAlgorithm[PreparedData, ALSModel, Query, PredictedResult] {

      ...

      def train(data: PreparedData): ALSModel = ...

      ...
    }
    ```

    Simply add the new parameter `sc: SparkContext,` to `train()` function signature:

    ```scala
    class ALSAlgorithm(val ap: ALSAlgorithmParams)
      extends P2LAlgorithm[PreparedData, ALSModel, Query, PredictedResult] {

      ...

      def train(sc: SparkContext, data: PreparedData): ALSModel = ...

      ...
    }
    ```

    You need to add the following import for your algorithm as well if it is not there:

    ```scala
    import org.apache.spark.SparkContext
    ```

2. Modify the file `build.sbt` in your template directory to use `pioVersion.value` as the version of org.apache.predictionio.core dependency:

    Under your template's root directory, you should see a file `build.sbt` which has the following content:

    ```
    libraryDependencies ++= Seq(
      "org.apache.predictionio"    %% "core"          % "0.8.6" % "provided",
      "org.apache.spark" %% "spark-core"    % "1.2.0" % "provided",
      "org.apache.spark" %% "spark-mllib"   % "1.2.0" % "provided")
    ```

    Change the version of `"org.apache.predictionio" && "core"` to `pioVersion.value`:

    ```
    libraryDependencies ++= Seq(
      "org.apache.predictionio"    %% "core"          % pioVersion.value % "provided",
      "org.apache.spark" %% "spark-core"    % "1.2.0" % "provided",
      "org.apache.spark" %% "spark-mllib"   % "1.2.0" % "provided")
    ```

3. Create a new file `pio-build.sbt` in template's **project/** directory with the following content:

    ```
    addSbtPlugin("org.apache.predictionio" % "pio-build" % "0.9.0")
    ```

    Then, you should see the following two files in the **project/** directory:

    ```
    your_template_directory$ ls project/
    assembly.sbt  pio-build.sbt
    ```

4. Create a new file `template.json` file in the engine template's root directory with the following content:

    ```
    {"pio": {"version": { "min": "0.9.0" }}}
    ```

    This is to specify the minium PredictionIO version which the engine can run with.

5. Lastly, you can add `/pio.sbt` into your engine template's `.gitignore`. `pio.sbt` is automatically generated by `pio build`.

That's it! Now you can run `pio build`, `pio train` and `pio deploy` with PredictionIO 0.9.0 in the same way as before!


##Upgrade to 0.8.4

**engine.json** has slightly changed its format in 0.8.4 in order to make engine more flexible. If you are upgrading to 0.8.4, engine.json needs to have the ```params``` field for *datasource*, *preparator*, and *serving*. Here is the sample engine.json from templates/scala-parallel-recommendation-custom-preparator that demonstrate the change for *datasource* (line 7).


```
In 0.8.3
{
  "id": "default",
  "description": "Default settings",
  "engineFactory": "org.template.recommendation.RecommendationEngine",
  "datasource": {
    "appId": 1
  },
  "algorithms": [
    {
      "name": "als",
      "params": {
        "rank": 10,
        "numIterations": 20,
        "lambda": 0.01
      }
    }
  ]
}
```



```
In 0.8.4
{
  "id": "default",
  "description": "Default settings",
  "engineFactory": "org.template.recommendation.RecommendationEngine",
  "datasource": {
    "params" : {
      "appId": 1
    }
  },
  "algorithms": [
    {
      "name": "als",
      "params": {
        "rank": 10,
        "numIterations": 20,
        "lambda": 0.01
      }
    }
  ]
```



##Upgrade from 0.8.2 to 0.8.3

0.8.3 disallows entity types **pio_user** and **pio_item**. These types are used by default for most SDKs. They are deprecated in 0.8.3, and SDKs helper functions have been updated to use **user** and **item** instead.

If you are upgrading to 0.8.3, you can follow these steps to migrate your data.

##### 1. Create a new app

```
$ pio app new <my app name>
```
Please take note of the <new app id> generated for the new app.

##### 2. Run the upgrade command

```
$ pio upgrade 0.8.2 0.8.3 <old app id> <new app id>
```

It will run a script that creates a new app with the new app id and migreate the data to the new app.

##### 3. Update **engine.json** to use the new app id. **Engine.json** is located under your engine project directory.

```
  "datasource": {
    "appId": <new app id>
  },
```

## Schema Changes in 0.8.2

0.8.2 contains HBase and Elasticsearch schema changes from previous versions. If you are upgrading from a pre-0.8.2 version, you need to first clear HBase and ElasticSearch. These will clear out all data
in Elasticsearch and HBase. Please be extra cautious.

DANGER: **ALL EXISTING DATA WILL BE LOST!**


### Clearing Elasticsearch

With Elasticsearch running, do

```
$ curl -X DELETE http://localhost:9200/_all
```

For details see http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/indices-delete-index.html.

### Clearing HBase

```
$ $HBASE_HOME/bin/hbase shell
...
> disable_all 'predictionio.*'
...
> drop_all 'predictionio.*'
...
```

For details see http://wiki.apache.org/hadoop/Hbase/Shell.

## Experimental upgrade tool (Upgrade HBase schema from 0.8.0/0.8.1 to 0.8.2)

Create an app to store the data

```
$ bin/pio app new <my app>
```

Replace by the returned app ID: ( is the original app ID used in 0.8.0/0.8.2.)

```
$ set -a
$ source conf/pio-env.sh
$ set +a
$ sbt/sbt "data/run-main org.apache.predictionio.data.storage.hbase.upgrade.Upgrade <from app ID>" "<to app ID>"
```
