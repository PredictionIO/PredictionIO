---
title: Frequently Asked Questions
---
## Using PredictionIO

### Q: How do I check to see if various dependencies, such as ElasticSearch and HBase, are running?

You can run `$ pio status` from the terminal and it will return the status of various components that PredictionIO depends on.

-  You should see the following message if everything is OK:

```
$ pio status
PredictionIO
  Installed at: /home/vagrant/PredictionIO
  Version: 0.8.6

Apache Spark
  Installed at: /home/vagrant/PredictionIO/vendors/spark-1.2.0
  Version: 1.2.0 (meets minimum requirement of 1.2.0)

Storage Backend Connections
  Verifying Meta Data Backend
  Verifying Model Data Backend
  Verifying Event Data Backend
  Test write Event Store (App Id 0)
2015-02-03 18:52:38,904 INFO  hbase.HBLEvents - The table predictionio_eventdata:events_0 doesn't exist yet. Creating now...
2015-02-03 18:52:39,868 INFO  hbase.HBLEvents - Removing table predictionio_eventdata:events_0...

(sleeping 5 seconds for all messages to show up...)
Your system is all ready to go.
```

- If you see the following error message, it usually means ElasticSearch is not running properly:

```
  ...
Storage Backend Connections
  Verifying Meta Data Backend
  ...
Caused by: org.elasticsearch.client.transport.NoNodeAvailableException: None of the configured nodes are available: []
	at org.elasticsearch.client.transport.TransportClientNodesService.ensureNodesAreAvailable(TransportClientNodesService.java:298)
  ...

Unable to connect to all storage backend(s) successfully. Please refer to error message(s) above. Aborting.
```

You can check if there is any ElasticSearch process by running 'jps'.

Please see **How to start elasticsearch** below.

- If you see the following error message, it usually means HBase is not running properly:

```
Storage Backend Connections
  Verifying Meta Data Backend
  Verifying Model Data Backend
  Verifying Event Data Backend
2015-02-03 18:40:04,810 ERROR zookeeper.RecoverableZooKeeper - ZooKeeper exists failed after 1 attempts
2015-02-03 18:40:04,812 ERROR zookeeper.ZooKeeperWatcher - hconnection-0x1e4075ce, quorum=localhost:2181, baseZNode=/hbase Received unexpected KeeperException, re-throwing exception
org.apache.zookeeper.KeeperException$ConnectionLossException: KeeperErrorCode = ConnectionLoss for /hbase/hbaseid
...
2015-02-03 18:40:07,021 ERROR hbase.StorageClient - Failed to connect to HBase. Plase check if HBase is running properly.
2015-02-03 18:40:07,026 ERROR storage.Storage$ - Error initializing storage client for source HBASE
2015-02-03 18:40:07,027 ERROR storage.Storage$ - Can't connect to ZooKeeper
java.util.NoSuchElementException: None.get
...

Unable to connect to all storage backend(s) successfully. Please refer to error message(s) above. Aborting.
```

You can check if there is any HBase-related process by running 'jps'.

Please see **How to start HBase** below.

### Q: How to start ElasticSearch?

If you used the [install script](/install/install-linux/#quick-install) to install PredictionIO, the ElasticSearch is installed at `~/PredictionIO/vendors/elasticsearch-x.y.z/` where x.y.z is the
version number (currently it's 1.4.4). To start it, run:

```
$ ~/PredictionIO/vendors/elasticsearch-x.y.z/bin/elasticsearch
```

If you didn't use install script, please go to where ElasticSearch is installed to start it.

INFO: It may take some time (15 seconds or so) for ElasticSearch to become ready after you start it (wait a bit before you run `pio status` again).

### Q: How to start HBase ?

If you used the [install script](/install/install-linux/#quick-install) to install PredictionIO, the HBase is installed at `~/PredictionIO/vendors/hbase-x.y.z/` where x.y.z is the version number (currently it's 0.98.6). To start it, run:

```
$ ~/PredictionIO/vendors/hbase-x.y.z/bin/start-hbase.sh
```

If you didn't use install script, please go to where HBase is installed to start it.

INFO: It may take some time (15 seconds or so) for HBase to become ready after you start it (wait a bit before you run `pio status` again).


## Problem with Event Server

### Q: How do I increase the JVM heap size of the Event Server?

Add the `JAVA_OPTS` environmental variable to supply JVM options, e.g.

```
$ JAVA_OPTS=-Xmx16g bin/pio eventserver ...
````

## Engine Training

### Q: How to increase Spark driver program and worker executor memory size?
In general, the PredictionIO `bin/pio` scripts wraps around Spark's `spark-submit`
script. You can specify a lot of Spark configurations (i.e. executor memory, cores, master
url, etc.) with it. You can supply these as pass-through arguments at the end of
`bin/pio` command.

If the engine training seems stuck, it's possible that the the executor doesn't have enough memory.

First, follow [instruction here]( http://spark.apache.org/docs/latest/spark-standalone.html) to start standalone Spark cluster and get the master URL. If you use the provided quick install script to install PredictionIO, the Spark is installed at `PredictionIO/vendors/spark-1.2.0/` where you could run the Spark commands in `sbin/` as described in the Spark documentation. Then use following train commmand to specify executor memory (default is only 512 MB) and driver memory.

For example, the follow command set the Spark master to `spark://localhost:7077`
(the default url of standalone cluster), set the driver memory to 16G and set the executor memory to 24G for `pio train`.

```
$ pio train -- --master spark://localhost:7077 --driver-memory 16G --executor-memory 24G
```

### Q: How to resolve "Exception in thread "main" org.apache.spark.SparkException: Job aborted due to stage failure: Serialized task 165:35 was 110539813 bytes, which exceeds max allowed: spark.akka.frameSize (10485760 bytes) - reserved (204800 bytes). Consider increasing spark.akka.frameSize or using broadcast variables for large values."?

A likely reason is the local algorithm model is larger than the default frame size.
You can specify a larger value as a pass-thru argument to spark-submit when you `pio train`.
The following command increase the frameSize to 1024MB.

```
$ pio train -- --conf spark.akka.frameSize=1024
```

## Deploy Engine

### Q: How to increase heap space memory for "pio deploy"?

If you see the following error during `pio deploy`, it means there is not enough heap space memory.

```
...
[ERROR] [LocalFSModels] Java heap space
[ERROR] [OneForOneStrategy] None.get
...
```

To increase the heap space, specify the "-- --driver-memory " parameter in the command. For example, set the driver memory to 8G when deploy the engine:

```
$ pio deploy -- --driver-memory 8G
```


## Building PredictionIO

### Q: How to resolve "Error: Could not find or load main class org.apache.predictionio.tools.Console" after ./make_distribution.sh?

```
$ bin/pio app
Error: Could not find or load main class org.apache.predictionio.tools.Console
```

When PredictionIO bumps a version, it creates another JAR file with the new
version number.

Delete everything but the latest `pio-assembly-<VERSION>.jar` in
`$PIO_HOME/assembly` directory. For example:

```
PredictionIO$ cd assembly/
PredictionIO/assembly$ ls -al
total 197776
drwxr-xr-x  2 yipjustin yipjustin      4096 Nov 12 00:08 .
drwxr-xr-x 17 yipjustin yipjustin      4096 Nov 12 00:09 ..
-rw-r--r--  1 yipjustin yipjustin 101184982 Nov  5 06:05 pio-assembly-0.8.1-SNAPSHOT.jar
-rw-r--r--  1 yipjustin yipjustin 101324859 Nov 12 00:09 pio-assembly-0.8.2.jar

PredictionIO/assembly$ rm pio-assembly-0.8.1-SNAPSHOT.jar
```

### Q: How to resolve ".......[error] (data/compile:compile) java.lang.AssertionError: assertion failed: java.lang.AutoCloseable" when ./make_distribution.sh?

PredictionIO only support Java 7 or later. Please make sure you have the
correct Java version with the command:

```
$ javac -version
```

## Engine Development

### Q: What's the difference between P- and L- prefixed classes and functions?

PredictionIO v0.8 is built on the top of Spark, a massively scalable programming framework. A spark algorithm is different from conventional single machine algorithm in a way that spark algorithms use the [RDD](http://spark.apache.org/docs/1.0.1/programming-guide.html#resilient-distributed-datasets-rdds) abstraction as its primary data type.

PredictionIO framework natively support both RDD-based algorithms and traditional single-machine algorithms. For controllers prefixed by "P" (i.e. PJavaDataSource, PJavaAlgorithm), their data include RDD abstraction; For "L" controllers, they are traditional single machine algorithms.

## Running HBase

### Q: How to resolve 'Exception in thread "main" java.lang.NullPointerException at org.apache.hadoop.net.DNS.reverseDns(DNS.java:92)'?

HBase relies on reverse DNS be set up properly to function. If your network
configuration changes (such as working on a laptop with public WiFi hotspots),
there could be a chance that reverse DNS does not function properly. You can
install a DNS server on your own computer. Some users have reported that using
[Google Public DNS](https://developers.google.com/speed/public-dns/) would also
solve the problem.

If you have other questions, you can search or post on the [user
group](https://groups.google.com/forum/#!forum/predictionio-user) or [email the
core team](mailto:support@prediction.io) directly.
