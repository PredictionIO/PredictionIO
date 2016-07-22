---
title: Building the "HelloWorld" Engine
---

# Building the "HelloWorld" Engine

This is a step-by-step guide on building your first predictive engine on
PredictionIO. The engine will use historical temperature data to predict the
temperature of a certain day in a week.

> You need to build PredictionIO from source in order to build your own engine.
Please follow instructions to build from source
[here](/install/install-sourcecode.html).

Completed source code can also be found at
`$PIO_HOME/examples/scala-local-helloworld` and
`$PIO_HOME/examples/java-local-helloworld`, where `$PIO_HOME` is the root
directory of the PredictionIO source code tree.


## Data Set

This engine will read a historial daily temperatures as training data set. A very simple data set is prepared for you.

First, create a directory somewhere and copy the data set over. Replace `path/to/data.csv` with your path which stores the training data.

```console
$ cp $PIO_HOME/examples/data/helloworld/data1.csv path/to/data.csv
```

## 1. Create a new Engine

```console
$ $PIO_HOME/bin/pio new HelloWorld
$ cd HelloWorld
```

A new engine project directory `HelloWorld` is created. You should see the following files being created inside this new project directory:

```
build.sbt
engine.json
params/
project/
src/
```

<div class="tabs">
  <div data-tab="Scala" data-lang="scala">
You can find the Scala engine template in <code>src/main/scala/Engine.scala</code>. Please follow the instructions below to edit this file.
  </div>
  <div data-tab="Java" data-lang="java">

<strong>NOTE:</strong>
The template is created for Scala codes. For Java, need to do the following:

Under <code>HelloWorld</code> directory:

```bash
$ rm -rf src/main/scala
$ mkdir -p src/main/java
```

  </div>
</div>

## 2. Define Data Types

### Define Training Data

<div class="tabs">
  <div data-tab="Scala" data-lang="scala">

Edit <code>src/main/scala/Engine.scala</code>:

```scala
class MyTrainingData(
  val temperatures: List[(String, Double)]
) extends Serializable
```
  </div>
  <div data-tab="Java" data-lang="java">

Create a new file <code>src/main/java/MyTrainingData.java</code>:

```java
package myorg;

import java.io.Serializable;
import java.util.List;

public class MyTrainingData implements Serializable {
  List<DayTemperature> temperatures;

  public MyTrainingData(List<DayTemperature> temperatures) {
    this.temperatures = temperatures;
  }

  public static class DayTemperature implements Serializable {
    String day;
    Double temperature;

    public DayTemperature(String day, Double temperature) {
      this.day = day;
      this.temperature = temperature;
    }
  }
}
```
  </div>
</div>

### Define Query

<div class="tabs">
  <div data-tab="Scala" data-lang="scala">

Edit <code>src/main/scala/Engine.scala</code>:

```scala
class MyQuery(
  val day: String
) extends Serializable
```
  </div>
  <div data-tab="Java" data-lang="java">

Create a new file <code>src/main/java/MyQuery.java</code>:

```java
package myorg;

import java.io.Serializable;

public class MyQuery implements Serializable {
  String day;

  public MyQuery(String day) {
    this.day = day;
  }
}
```
  </div>
</div>

### Define Model
<div class="tabs">
  <div data-tab="Scala" data-lang="scala">

Edit <code>src/main/scala/Engine.scala</code>:

```scala
import scala.collection.immutable.HashMap

class MyModel(
  val temperatures: HashMap[String, Double]
) extends Serializable {
  override def toString = temperatures.toString
}

```
  </div>
  <div data-tab="Java" data-lang="java">

Create a new file <code>src/main/java/MyModel.java</code>:

```java
package myorg;

import java.io.Serializable;
import java.util.Map;

public class MyModel implements Serializable {
  Map<String, Double> temperatures;

  public MyModel(Map<String, Double> temperatures) {
    this.temperatures = temperatures;
  }

  @Override
  public String toString() {
    return temperatures.toString();
  }
}
```
  </div>
</div>

### Define Predicted Result
<div class="tabs">
  <div data-tab="Scala" data-lang="scala">

Edit <code>src/main/scala/Engine.scala</code>:

```scala
class MyPredictedResult(
  val temperature: Double
) extends Serializable
```
  </div>
  <div data-tab="Java" data-lang="java">

Create a new file <code>src/main/java/MyPredictedResult.java</code>:

```java
package myorg;

import java.io.Serializable;

public class MyPredictedResult implements Serializable {
  Double temperature;

  public MyPredictedResult(Double temperature) {
    this.temperature = temperature;
  }
}
```
  </div>
</div>

## 3. Implement the Data Source

<div class="tabs">
  <div data-tab="Scala" data-lang="scala">

Edit <code>src/main/scala/Engine.scala</code>:

```scala
import scala.io.Source

class MyDataSource extends LDataSource[EmptyDataSourceParams, EmptyDataParams,
                                MyTrainingData, MyQuery, EmptyActualResult] {

  override def readTraining(): MyTrainingData = {
    val lines = Source.fromFile("path/to/data.csv").getLines()
      .toList.map { line =>
        val data = line.split(",")
        (data(0), data(1).toDouble)
      }
    new MyTrainingData(lines)
  }

}
```
  </div>
  <div data-tab="Java" data-lang="java">

Create a new file <code>src/main/java/MyDataSource.java</code>:

```java
package myorg;

import org.apache.predictionio.controller.java.*;

import java.util.List;
import java.util.ArrayList;
import java.io.FileReader;
import java.io.BufferedReader;

public class MyDataSource extends LJavaDataSource<
  EmptyDataSourceParams, EmptyDataParams, MyTrainingData, MyQuery, EmptyActualResult> {

  @Override
  public MyTrainingData readTraining() {
    List<MyTrainingData.DayTemperature> temperatures =
                  new ArrayList<MyTrainingData.DayTemperature>();

    try {
      BufferedReader reader =
                    new BufferedReader(new FileReader("path/to/data.csv"));
      String line;
      while ((line = reader.readLine()) != null) {
        String[] tokens = line.split(",");
        temperatures.add(
          new MyTrainingData.DayTemperature(tokens[0],
                            Double.parseDouble(tokens[1])));
      }
      reader.close();
    } catch (Exception e) {
      System.exit(1);
    }

    return new MyTrainingData(temperatures);
  }
}
```
  </div>
</div>

**NOTE**: You need to update the `path/to/data.csv` in this code with the correct path that store the training data.


## 4. Implement an Algorithm

<div class="tabs">
  <div data-tab="Scala" data-lang="scala">

Edit <code>src/main/scala/Engine.scala</code>:

```scala
class MyAlgorithm extends LAlgorithm[EmptyAlgorithmParams, MyTrainingData,
  MyModel, MyQuery, MyPredictedResult] {

  override
  def train(pd: MyTrainingData): MyModel = {
    // calculate average value of each day
    val average = pd.temperatures
      .groupBy(_._1) // group by day
      .mapValues{ list =>
        val tempList = list.map(_._2) // get the temperature
        tempList.sum / tempList.size
      }

    // trait Map is not serializable, use concrete class HashMap
    new MyModel(HashMap[String, Double]() ++ average)
  }

  override
  def predict(model: MyModel, query: MyQuery): MyPredictedResult = {
    val temp = model.temperatures(query.day)
    new MyPredictedResult(temp)
  }
}
```
  </div>
  <div data-tab="Java" data-lang="java">
Create a new file <code>src/main/java/MyAlgorithm.java</code>:

```java
package myorg;

import org.apache.predictionio.controller.java.*;

import java.util.Map;
import java.util.HashMap;

public class MyAlgorithm extends LJavaAlgorithm<
  EmptyAlgorithmParams, MyTrainingData, MyModel, MyQuery, MyPredictedResult> {

  @Override
  public MyModel train(MyTrainingData data) {
    Map<String, Double> sumMap = new HashMap<String, Double>();
    Map<String, Integer> countMap = new HashMap<String, Integer>();

    // calculate sum and count for each day
    for (MyTrainingData.DayTemperature temp : data.temperatures) {
      Double sum = sumMap.get(temp.day);
      Integer count = countMap.get(temp.day);
      if (sum == null) {
        sumMap.put(temp.day, temp.temperature);
        countMap.put(temp.day, 1);
      } else {
        sumMap.put(temp.day, sum + temp.temperature);
        countMap.put(temp.day, count + 1);
      }
    }

    // calculate the average
    Map<String, Double> averageMap = new HashMap<String, Double>();
    for (Map.Entry<String, Double> entry : sumMap.entrySet()) {
      String day = entry.getKey();
      Double average = entry.getValue() / countMap.get(day);
      averageMap.put(day, average);
    }

    return new MyModel(averageMap);
  }

  @Override
  public MyPredictedResult predict(MyModel model, MyQuery query) {
    Double temp = model.temperatures.get(query.day);
    return new MyPredictedResult(temp);
  }
}
```
  </div>
</div>

## 5. Implement EngineFactory

<div class="tabs">
  <div data-tab="Scala" data-lang="scala">

Edit <code>src/main/scala/Engine.scala</code>:

```scala
object MyEngineFactory extends IEngineFactory {
  override
  def apply() = {
    /* SimpleEngine only requires one DataSouce and one Algorithm */
    new SimpleEngine(
      classOf[MyDataSource],
      classOf[MyAlgorithm]
    )
  }
}
```
  </div>
  <div data-tab="Java" data-lang="java">
Create a new file <code>src/main/java/MyEngineFactory.java</code>:

```java
package myorg;

import org.apache.predictionio.controller.java.*;

public class MyEngineFactory implements IJavaEngineFactory {
  public JavaSimpleEngine<MyTrainingData, EmptyDataParams, MyQuery, MyPredictedResult,
    EmptyActualResult> apply() {

    return new JavaSimpleEngineBuilder<MyTrainingData, EmptyDataParams,
      MyQuery, MyPredictedResult, EmptyActualResult> ()
      .dataSourceClass(MyDataSource.class)
      .preparatorClass() // Use default Preparator
      .addAlgorithmClass("", MyAlgorithm.class)
      .servingClass() // Use default Serving
      .build();
  }
}

```
  </div>
</div>

## 6. Define engine.json

You should see an engine.json created as follows:

```json
{
  "id": "helloworld",
  "version": "0.0.1-SNAPSHOT",
  "name": "helloworld",
  "engineFactory": "myorg.MyEngineFactory"
}
```

If you follow this Hello World Engine tutorial and didn't modify any of the class and package name (`myorg`). You don't need to update this file.

## 7. Define Parameters

You can safely delete the file `params/datasoruce.json` because this Hello World Engine doesn't take any parameters.

```
$ rm params/datasource.json
```

# Deploying the "HelloWorld" Engine Instance

After the new engine is built, it is time to deploy an engine instance of it.

## 1. Register engine:

```bash
$ $PIO_HOME/bin/pio register
```

This command will compile the engine source code and build the necessary binary.

## 2. Train:

```bash
$ $PIO_HOME/bin/pio train
```

Example output:

```
2014-09-18 15:44:57,568 INFO  spark.SparkContext - Job finished: collect at Workflow.scala:677, took 0.138356 s
2014-09-18 15:44:57,757 INFO  workflow.CoreWorkflow$ - Saved engine instance with ID: zdoo7SGAT2GVX8dMJFzT5w
```

This command produce an Engine Instance, which can be deployed.

## 3. Deploy:

```bash
$ $PIO_HOME/bin/pio deploy
```

You should see the following if the engine instance is deploy sucessfully:

```
INFO] [10/13/2014 18:11:09.721] [pio-server-akka.actor.default-dispatcher-4] [akka://pio-server/user/IO-HTTP/listener-0] Bound to localhost/127.0.0.1:8000
[INFO] [10/13/2014 18:11:09.724] [pio-server-akka.actor.default-dispatcher-7] [akka://pio-server/user/master] Bind successful. Ready to serve.
```

Do not kill the deployed Engine Instance. You can retrieve the prediction by sending HTTP request to the engine instance.

Open another terminal to execute the following:

Retrieve temperature prediction for Monday:

```bash
$ curl -H "Content-Type: application/json" -d '{ "day": "Mon" }' http://localhost:8000/queries.json
```

You should see the following output:

```json
{"temperature":75.5}
```

You can send another query to retrieve prediction. For example, retrieve temperature prediction for Tuesday:

```bash
$ curl -H "Content-Type: application/json" -d '{ "day": "Tue" }' http://localhost:8000/queries.json
```

You should see the following output:

```json
{"temperature":80.5}
```

# Re-training The Engine

Let's say you have collected more historial temperature data and want to re-train the Engine with updated data. You can simply execute `pio train` and `pio deploy` again.

Another temperature data set is prepared for you. Run the following to update your data with this new data set. Replace the `path/to/data.csv` with your path used in the steps above.

```bash
$ cp $PIO_HOME/examples/data/helloworld/data2.csv path/to/data.csv
```

In another terminal, go to the `HelloWorld` engine directory. Execute `pio train` and `deploy` again to deploy the latest instance trained with the new data. It would automatically kill the old running engine instance.

```bash
$ $PIO_HOME/bin/pio train
$ $PIO_HOME/bin/pio deploy
```

Retrieve temperature prediction for Monday again:

```bash
$ curl -H "Content-Type: application/json" -d '{ "day": "Mon" }' http://localhost:8000/queries.json
```

You should see the following output:

```json
{"temperature":76.66666666666667}
```

Check out [Java Parallel Helloworld tutorial](parallel-helloworld.html)
if you are interested how things are done on the parallel side.
