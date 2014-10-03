---
layout: docs
title: Installing PredictionIO from Source Code
---

# Installing PredictionIO from Source Code

## Building

Run the following to download and build PredictionIO from its source code.

```
$ git clone https://github.com/PredictionIO/PredictionIO.git
$ cd PredictionIO
$ ./make-distribution.sh
```

You should see something like the following when it finishes building
successfully.

```
...
PredictionIO-{{ site.pio_version }}/sbt/sbt
PredictionIO-{{ site.pio_version }}/conf/
PredictionIO-{{ site.pio_version }}/conf/pio-env.sh
PredictionIO binary distribution created at PredictionIO-{{ site.pio_version }}.tar.gz
```


## Installing Dependencies

### Spark Setup

Apache Spark is the default processing engine for PredictionIO. Download [Apache
Spark release 1.1.0 package hadoop2.4](http://spark.apache.org/downloads.html).


```
$ wget http://d3kbcqa49mib13.cloudfront.net/{{ site.spark_download_filename }}.tgz
$ tar zxvf {{ site.spark_download_filename }}.tgz
```

Copy the configuration template `conf/pio-env.sh.template` to `conf/pio-env.sh`
in your PredictionIO installation directory. After that, edit `conf/pio-env.sh`
and point `SPARK_HOME` to the location where you extracted Apache Spark.

```
SPARK_HOME=/home/abc/Downloads/{{ site.spark_download_filename }}
```

### Storage Setup

#### Elasticsearch Setup

By default, PredictionIO uses Elasticsearch at localhost as the data store to
store its metadata. Simply install and run
[Elasticsearch](http://www.elasticsearch.org/), which looks like this:

```
$ wget https://download.elasticsearch.org/elasticsearch/elasticsearch/{{ site.elasticsearch_download_filename }}.tar.gz
$ tar zxvf {{ site.elasticsearch_download_filename }}.tar.gz
$ cd {{ site.elasticsearch_download_filename }}
$ bin/elasticsearch
```

You may change the settings or even use another data store such as
[MongoDB](http://www.mongodb.org/). For details, please read [Changing the Data
Store](config-datastore.html).

#### <a name="hbase"></a>HBase Setup

By default, PredictionIO's Data API uses [HBase](http://hbase.apache.org/) at localhost as the data store
for event data.

```
$ wget https://archive.apache.org/dist/hbase/{{ site.hbase_basename }}/{{ site.hbase_basename }}-{{ site.hbase_variant }}.tar.gz
$ tar zxvf {{ site.hbase_basename }}-{{ site.hbase_variant }}.tar.gz
$ cd {{ site.hbase_basename }}-{{ site.hbase_variant }}
```

You will need to at least add a minimal configuration to HBase to start it in
standalone mode. Details can be found
[here](http://hbase.apache.org/book/quickstart.html). Here, we are showing a
sample minimal configuration.

> For production deployment, run a fully distributed HBase configuration.

Edit `conf/hbase-site.xml` and put the following in. You may replace `/home/abc`
with your own home directory.

```
<configuration>
  <property>
    <name>hbase.rootdir</name>
    <value>file:///home/abc/hbase</value>
  </property>
  <property>
    <name>hbase.zookeeper.property.dataDir</name>
    <value>/home/abc/zookeeper</value>
  </property>
</configuration>
```

Edit `conf/hbase-env.sh` to set `JAVA_HOME` for the cluster. For Mac users it would be

```
export JAVA_HOME=`/usr/libexec/java_home -v 1.7`
```

Now you may start HBase.

```
$ bin/start-hbase.sh
```

Now you have installed everything you need to run PredictionIO!

Next: Reading [Quick Start]({{site.baseurl}}/tutorials/engines/quickstart.html)
