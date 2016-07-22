---
title: Quick Start - Using a Built-in Engine
---

# Quick Start - Using a Built-in Engine
<code>This doc is applicable to 0.8.0 only. Updated version for 0.8.2 will be available soon.</code>


This is a quick start guide of using a PredictionIO's built-in engine and its
SDKs to write a very simple app. It assumes that you have [installed
PredictionIO server](/install).

Let's start with a classic example in Machine Learning - build a ranking
engine. We are going to launch a ranking engine instance that can:

* collect *real-time event data* from your app through REST API or SDKs;
* update the predictive model with *new data* regularly and automatically;
* answer *prediction query* through REST API or SDKs.

> **Notes about HADOOP_CONF_DIR**

> Before you begin this tutorial, make sure your environment does not have the
variable `HADOOP_CONF_DIR` set. When this is set, PredictionIO will
automatically pick it up and some functionality will expect an operational
Hadoop 2 environment.

# Create a Simple App Project

Create a new project directory for a simple app that will use the engine.

```
$ mkdir quickstartapp
$ cd quickstartapp
```

# Install SDK

To communicate with PredictionIO server, we can use a PredictionIO SDK of a
specific programming language:

<div class="tabs">
  <div data-tab="PHP SDK" data-lang="php">
<p>To use the PredictionIO PHP SDK, we are going to install it with Composer:</p>
<p>1. Create a file called ``composer.json`` in your project directory, which adds predictionorg.apache.predictionioio as a dependency. It should look like this:</p>

```json
{
    "require": {
        "predictionorg.apache.predictionioio": "~0.8.0"
    }
}
```

<p>2. Install Composer:</p>

```bash
$ curl -sS https://getcomposer.org/installer | php -d detect_unicode=Off
```

<p>3. Use Composer to install your dependencies:</p>

```bash
$ php composer.phar install
```

<p>Now you are ready to write the actual PHP code.</p>
  </div>
  <div data-tab="Python SDK" data-lang="python">
```bash
$ pip install predictionio
```
or
```bash
$ easy_install predictionio
```
  </div>
  <div data-tab="Ruby SDK" data-lang="ruby">
```ruby
$ gem install predictionio
```
  </div>
  <div data-tab="Java SDK" data-lang="java">
To use PredictionIO in your project, add this to the <code>dependencies</code>
section of your project's <code>pom.xml</code> file:
```bash
<dependencies>
  <dependency>
    <groupId>org.apache.predictionio</groupId>
    <artifactId>client</artifactId>
    <version>0.8.0</version>
  </dependency>
</dependencies>
```

To run examples in PredictionIO Java SDK, clone the PredictionIO-Java-SDK
repository and build it using Maven:
```bash
$ cd ~
$ git clone git://github.com/PredictionIO/PredictionIO-Java-SDK.git
$ cd PredictionIO-Java-SDK
$ mvn clean install
```
Javadoc appears in client/target/apidocs/index.html.
  </div>
</div>


# Collect Data into PredictionIO

## Launch the Event Server

```bash
$ $PIO_HOME/bin/pio eventserver
```

where `$PIO_HOME` is the installation directory of PredictionIO. As long as the
Event Server is running, PredictionIO keeps listening to new data.

To bind to a different address, 
```bash
$ $PIO_HOME/bin/pio eventserver --ip <IP>
```

## Collecting Data

We are going to write a script that generates some random data and simulates
data collection. With the *EventClient* of one of the PredictionIO SDKs, your
application can send data to the Event Server in real-time easily through the
[EventAPI](/eventapi.html). In the *quickstartapp* directory:

<div class="tabs">
  <div data-tab="PHP SDK" data-lang="php">
<p>Create <em>import.php</em> as below. Replace <code>your_app_id</code> with
your app id (integer).</p>
```php
<?php
    // use composer's autoloader to load PredictionIO PHP SDK
    require_once("vendor/autoload.php");
    use predictionio\EventClient;

    $client = new EventClient(your_app_id);

    // generate 10 users, with user ids 1,2,....,10
    for ($i=1; $i<=10; $i++) {
        echo "Add user ". $i . "\n";
        $response=$client->setUser($i);
    }

    // generate 50 items, with item ids 1,2,....,50
    // assign type id 1 to all of them
    for ($i=1; $i<=50; $i++) {
        echo "Add item ". $i . "\n";
        $response=$client->setItem($i, array('pio_itypes'=>array('1')));
    }

    // each user randomly views 10 items
    for ($u=1; $u<=10; $u++) {
        for ($count=0; $count<10; $count++) {
            $i = rand(1, 50); // randomly pick an item
            echo "User ". $u . " views item ". $i ."\n";
            $response=$client->recordUserActionOnItem('view', $u, $i);
        }
    }
?>
```
and run it:
```php
$ php import.php
```
  </div>
  <div data-tab="Python SDK" data-lang="python">
<p>Create <em>import.py</em> as below. Replace <code>your_app_id</code> with
your app id (integer).</p>

```python
import predictionio
import random

random.seed()

client = predictionio.EventClient(app_id=your_app_id)

# generate 10 users, with user ids 1,2,....,10
user_ids = [str(i) for i in range(1, 11)]
for user_id in user_ids:
  print "Set user", user_id
  client.set_user(user_id)

# generate 50 items, with item ids 1,2,....,50
# assign type id 1 to all of them
item_ids = [str(i) for i in range(1, 51)]
for item_id in item_ids:
  print "Set item", item_id
  client.set_item(item_id, {
    "pio_itypes" : ['1']
  })

# each user randomly views 10 items
for user_id in user_ids:
  for viewed_item in random.sample(item_ids, 10):
    print "User", user_id ,"views item", viewed_item
    client.record_user_action_on_item("view", user_id, viewed_item)

client.close()

```
and run it:
```bash
$ python import.py
```

  </div>
  <div data-tab="Ruby SDK" data-lang="ruby">
<p>Create <em>import.rb</em> as below. Replace <code>your_app_id</code> with
your app id (integer).</p>

```ruby
require 'predictionio'

# Instantiate an EventClient
client = PredictionIO::EventClient.new(your_app_id)

# Generate 10 users, with user IDs 1 to 10.
(1..10).each do |uid|
  puts "Add user #{uid}"
  client.set_user(uid)
end

# Generate 50 items, with item IDs 1 to 10.
(1..50).each do |iid|
  puts "Add item #{iid}"
  client.set_item(iid, 'properties' => { 'pio_itypes' => %w(1) })
end

# Each user randomly views 10 items.
(1..10).each do |uid|
  (1..10).each do |count|
    iid = Random.rand(51)
    puts "User #{uid} views item #{iid}"
    client.record_user_action_on_item('view', uid.to_s, iid.to_s)
  end
end
```
and run it:
```bash
$ ruby import.rb
```
  </div>
  <div data-tab="Java SDK" data-lang="java">
<p><em>QuickstartImport.java</em> is located under
PredictionIO-Java-SDK/examples/quickstart_import/src/main/java/org.apache.predictionio/samples/.
Replace <code>your_app_id</code> with your app id (integer).</p>

```java
package org.apache.predictionio.samples;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.apache.predictionio.EventClient;

import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;

public class QuickstartImport {
    public static void main(String[] args)
            throws ExecutionException, InterruptedException, IOException {
        EventClient client = new EventClient(your_app_id);
        Random rand = new Random();
        Map<String, Object> emptyProperty = ImmutableMap.of();

        // generate 10 users, with user ids 1 to 10
        for (int user = 1; user <= 10; user++) {
            System.out.println("Add user " + user);
            client.setUser(""+user, emptyProperty);
        }

        // generate 50 items, with item ids 1 to 50
        // assign type id 1 to all of them
        Map<String, Object> itemProperty = ImmutableMap.<String, Object>of(
                "pio_itypes", ImmutableList.of("1"));
        for (int item = 1; item <= 50; item++) {
            System.out.println("Add item " + item);
            client.setItem(""+item, itemProperty);
        }

        // each user randomly views 10 items
        for (int user = 1; user <= 10; user++) {
            for (int i = 1; i <= 10; i++) {
                int item = rand.nextInt(50) + 1;
                System.out.println("User " + user + " views item " + item);
                client.userActionItem("view", ""+user, ""+item, emptyProperty);
            }
        }

        client.close();
    }
}
```
To compile and run it:
```bash
$ cd PredictionIO-Java-SDK/examples/quickstart_import
$ mvn clean compile assembly:single
$ java -jar target/quickstart-import-<latest version>-jar-with-dependencies.jar
```
  </div>
</div>




# Deploying an Engine Instance

Each engine deals with one type of Machine Learning task. For instance, Item
Ranking Engine (itemrank) makes personalized item (e.g. product or content)
ranking to your users.

> **What is an Engine Instance?**
>
> You can deploy one or more *engine instances* from an engine. It means that
you can run multiple ranking *engine instances* at the same time with different
settings, or even for different applications.

To deploy an engine instance for *quickstartapp*, first create an engine
instance project:

```bash
$ $PIO_HOME/bin/pio instance org.apache.predictionio.engines.itemrank
$ cd org.apache.predictionio.engines.itemrank
$ $PIO_HOME/bin/pio register
```

Edit `params/datasource.json` and modify the value of `appId` to fit your app.

Now, you can kick start the predictive model training with:

INFO: If you are using **Linux**, Apache Spark local mode, which is the default
operation mode without further configuration, may not work. In that case,
configure your Apache Spark to run in [standalone cluster
mode](http://spark.apache.org/docs/latest/spark-standalone.html).

```bash
$ $PIO_HOME/bin/pio train
...
2014-09-11 16:25:44,591 INFO  spark.SparkContext - Job finished: collect at Workflow.scala:674, took 0.078664 s
2014-09-11 16:25:44,737 INFO  workflow.CoreWorkflow$ - Saved engine instance with ID: KxOsC2FRSdGGe1lv0oaHiw
```

> **Notes for Apache Spark in Cluster Mode**

> If you are using an Apache Spark cluster, you will need to pass the cluster's
master URL to the `pio train` command, e.g.

> ```bash
$ $PIO_HOME/bin/pio train -- --master spark://`hostname`:7077
```

> You may replace the command `hostname` with your hostname, which can be found
> on [Spark's UI](http://localhost:8080).

If your training was successful, you should see the lines shown above. Now you are ready to deploy the instance:

```bash
$ $PIO_HOME/bin/pio deploy
...
[INFO] [09/11/2014 16:26:16.525] [pio-server-akka.actor.default-dispatcher-2] [akka://pio-server/user/IO-HTTP/listener-0] Bound to localhost/127.0.0.1:8000
[INFO] [09/11/2014 16:26:16.526] [pio-server-akka.actor.default-dispatcher-5] [akka://pio-server/user/master] Bind successful. Ready to serve.
```

Notice that the `deploy` command runs the engine instance in the foreground. You can also use the --ip option to bind to a different ip address. Now we are ready to take a look at the results! 


# Retrieve Prediction Results

With the *EngineClients* of a PredictionIO SDK, your application can send
queries to a deployed engine instance through the Engine API. In the
*quickstartapp* directory:

<div class="tabs">
<div data-tab="PHP SDK" data-lang="php">
<p>Create a file <em>show.php</em> with this code:</p>
```php
<?php
    // use composer's autoloader to load PredictionIO PHP SDK
    require_once("vendor/autoload.php");
    use predictionio\EngineClient;

    $client = new EngineClient();

    // Rank item 1 to 5 for each user
    for ($i=1; $i<=10; $i++) {
      $response=$client->sendQuery(array('uid'=>$i,
                           'iids'=>array(1,2,3,4,5)));
      print_r($response);
    }
?>
```
and run it:
```bash
$ php show.php
```
  </div>
  <div data-tab="Python SDK" data-lang="python">
<p>Create a file <em>show.py</em> with this code:</p>

```python
import predictionio

client = predictionio.EngineClient()

# Rank item 1 to 5 for each user
item_ids = [str(i) for i in range(1, 6)]
user_ids = [str(x) for x in range(1, 11)]
for user_id in user_ids:
  print "Rank item 1 to 5 for user", user_id
  try:
    response = client.send_query({
      "uid": user_id,
      "iids": item_ids
    })
    print response
  except predictionio.PredictionIOAPIError as e:
    print 'Caught exception:', e.strerror()

client.close()

```

and run it:

```bash
$ python show.py
```
  </div>
  <div data-tab="Ruby SDK" data-lang="ruby">
<p>Create a file <em>show.rb</em> with this code:</p>
```ruby
require 'predictionio'

client = PredictionIO::EngineClient.new

(1..10).each do |uid|
  predictions = client.send_query('uid' => uid.to_s, 'iids' => %w(1 2 3 4 5))
  puts predictions
end
```

and run it:

```bash
$ ruby show.rb
```
  </div>
  <div data-tab="Java SDK" data-lang="java">
<p><em>QuickstartShow.java</em> is located under
PredictionIO-Java-SDK/examples/quickstart_show/src/main/java/org.apache.predictionio/samples/.</p>

```java
package org.apache.predictionio.samples;

import com.google.common.collect.ImmutableList;

import org.apache.predictionio.EngineClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class QuickstartShow {
    public static void main(String[] args)
            throws ExecutionException, InterruptedException, IOException {
        EngineClient client = new EngineClient();

        // rank item 1 to 5 for each user
        Map<String, Object> query = new HashMap<>();
        query.put("iids", ImmutableList.of("1", "2", "3", "4", "5"));
        for (int user = 1; user <= 10; user++) {
            query.put("uid", user);
            System.out.println("Rank item 1 to 5 for user " + user);
            System.out.println(client.sendQuery(query));
        }

        client.close();
    }
}
```

To compile and run it:
```bash
$ cd PredictionIO-Java-SDK/examples/quickstart_show
$ mvn clean compile assembly:single
$ java -jar target/quickstart-show-<latest version>-jar-with-dependencies.jar
```
  </div>
</div>

Well done! You have created a simple, but production-ready app with PredictionIO
ranking engine.

Next: Learn more about [collecting data through Event API](/eventapi.html).
