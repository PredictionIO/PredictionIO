---
layout: docs
title: Building the "HelloWorld" Engine
---

# Building the "HelloWorld" Engine

This is a step-by-step guide on building your first predictive engine on
PredictionIO. The engine will use historical temperature data to predict the
temperature of a certain day in a week.

> You need to build PredictionIO from source in order to build your own engine.
Please follow instructions to build from source
[here]({{site.baseurl}}/install/install-sourcecode.html).

Completed source code can also be found at
`$PIO_HOME/examples/scala-local-helloworld` and
`$PIO_HOME/examples/java-local-helloworld`, where `$PIO_HOME` is the root
directory of the PredictionIO source code tree.

## 1. Create a new Engine

<div class="codetabs">
<div data-lang="Scala">
{% highlight bash %}
$ $PIO_HOME/bin/pio new HelloWorld
$ cd HelloWorld
{% endhighlight %}
Now you need to edit <code>src/main/scala/Engine.scala</code>.
</div>
<div data-lang="Java">
{% highlight bash %}
$ $PIO_HOME/bin/pio new HelloWorld
$ cd HelloWorld

$ rm -rf src/main/scala
$ mkdir src/main/java
{% endhighlight %}

Add the new classes under the directory <code>src/main/java</code>.
</div>
</div>



## 2. Define Data Types

### Define Training Data

<div class="codetabs">
<div data-lang="Scala">
{% highlight scala %}
class MyTrainingData(
  val temperatures: List[(String, Double)]
) extends Serializable
{% endhighlight %}
</div>
<div data-lang="Java">
{% highlight java %}
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
{% endhighlight %}
</div>
</div>

### Define Query

<div class="codetabs">
<div data-lang="Scala">
{% highlight scala %}
class MyQuery(
  val day: String
) extends Serializable
{% endhighlight %}
</div>
<div data-lang="Java">
{% highlight java %}
public class MyQuery implements Serializable {
  String day;

  public MyQuery(String day) {
    this.day = day;
  }
}
{% endhighlight %}
</div>
</div>

### Define Model
<div class="codetabs">
<div data-lang="Scala">
{% highlight scala %}
import scala.collection.immutable.HashMap

class MyModel(
  val temperatures: HashMap[String, Double]
) extends Serializable {
  override def toString = temperatures.toString
}

{% endhighlight %}
</div>
<div data-lang="Java">
{% highlight java %}
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
{% endhighlight %}
</div>
</div>

### Define Prediction Result
<div class="codetabs">
<div data-lang="Scala">
{% highlight scala %}
class MyPrediction(
  val temperature: Double
) extends Serializable
{% endhighlight %}
</div>
<div data-lang="Java">
{% highlight java %}
public class MyPrediction implements Serializable {
  Double temperature;

  public MyPrediction(Double temperature) {
    this.temperature = temperature;
  }
}
{% endhighlight %}
</div>
</div>

## 3. Implement the Data Source
<div class="codetabs">
<div data-lang="Scala">
{% highlight scala %}
import scala.io.Source

class MyDataSource extends LDataSource[EmptyDataSourceParams, EmptyDataParams,
                                MyTrainingData, MyQuery, EmptyActual] {

  override def readTraining(): MyTrainingData = {
    val lines = Source.fromFile("path/to/data.csv").getLines()
      .toList.map { line =>
        val data = line.split(",")
        (data(0), data(1).toDouble)
      }
    new MyTrainingData(lines)
  }

}
{% endhighlight %}
</div>
<div data-lang="Java">
{% highlight java %}
public class MyDataSource extends LJavaDataSource<
  EmptyDataSourceParams, EmptyDataParams, MyTrainingData, MyQuery, EmptyActual> {

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
{% endhighlight %}
</div>
</div>

## 4. Implement an Algorithm
<div class="codetabs">
<div data-lang="Scala">
{% highlight scala %}
class MyAlgorithm extends LAlgorithm[EmptyAlgorithmParams, MyTrainingData,
  MyModel, MyQuery, MyPrediction] {

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
  def predict(model: MyModel, query: MyQuery): MyPrediction = {
    val temp = model.temperatures(query.day)
    new MyPrediction(temp)
  }
}
{% endhighlight %}
</div>
<div data-lang="Java">
{% highlight java %}
public class MyAlgorithm extends LJavaAlgorithm<
  EmptyAlgorithmParams, MyTrainingData, MyModel, MyQuery, MyPrediction> {

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
  public MyPrediction predict(MyModel model, MyQuery query) {
    Double temp = model.temperatures.get(query.day);
    return new MyPrediction(temp);
  }
}
{% endhighlight %}
</div>
</div>

# Deploying the "HelloWorld" Engine Instance

After the new engine is built, it is time to deploy an engine instance of it.

Prepare training data:

{% highlight bash %}
$ cp $PIO_HOME/examples/data/helloworld/data1.csv path/to/data.csv
{% endhighlight %}

Register engine:

{% highlight bash %}
$ $PIO_HOME/bin/pio register
{% endhighlight %}

Train:

{% highlight bash %}
$ $PIO_HOME/bin/pio train
{% endhighlight %}

Example output:

```
2014-09-18 15:44:57,568 INFO  spark.SparkContext - Job finished: collect at Workflow.scala:677, took 0.138356 s
2014-09-18 15:44:57,757 INFO  workflow.CoreWorkflow$ - Saved engine instance with ID: zdoo7SGAT2GVX8dMJFzT5w
```

Deploy:

{% highlight bash %}
$ $PIO_HOME/bin/pio deploy
{% endhighlight %}

Retrieve prediction:

{% highlight bash %}
$ curl -H "Content-Type: application/json" -d '{ "day": "Mon" }' http://localhost:8000/queries.json
{% endhighlight %}

Output:

```json
{"temperature":75.5}
```

Retrieve prediction:

{% highlight bash %}
$ curl -H "Content-Type: application/json" -d '{ "day": "Tue" }' http://localhost:8000/queries.json
{% endhighlight %}

Output:

```json
{"temperature":80.5}
```

## Re-training

Re-train with new data:

{% highlight bash %}
$ cp $PIO_HOME/examples/data/helloworld/data2.csv path/to/data.csv
{% endhighlight %}

{% highlight bash %}
$ $PIO_HOME/bin/pio train
$ $PIO_HOME/bin/pio deploy
{% endhighlight %}

Retrieve prediction:

{% highlight bash %}
$ curl -H "Content-Type: application/json" -d '{ "day": "Mon" }' http://localhost:8000/queries.json
{% endhighlight %}

Output:

```json
{"temperature":76.66666666666667}
```
