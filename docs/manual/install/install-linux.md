---
layout: docs
title: Installing PredictionIO on Linux
---

# Installing PredictionIO on Linux

## Download PredictionIO

Simply download PredictionIO's binary distribution and extract it.

```
$ wget http://download.prediction.io/PredictionIO-{{ site.pio_version }}.tar.gz
$ tar zxvf PredictionIO-{{ site.pio_version }}.tar.gz
$ cd PredictionIO-{{ site.pio_version }}
```

## Installing Dependencies

### Spark Setup

Apache Spark is the default processing engine for PredictionIO. Download [Apache
Spark release 1.1.0 package hadoop2.4](http://spark.apache.org/downloads.html).
Extract the file, and set the `SPARK_HOME` configuration in `conf/pio-env.sh` to
the Spark directory.

```
$ wget http://d3kbcqa49mib13.cloudfront.net/{{ site.spark_download_filename }}.tgz
$ tar zxvf {{ site.spark_download_filename }}.tgz
```

After that, edit `conf/pio-env.sh` in your PredictionIO installation directory.
For example,

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

#### HBase Setup<a class="anchor" name="hbase">&nbsp;</a>

By default, PredictionIO's Data API uses HBase at localhost as the data store
for event data.

```
$ wget http://archive.apache.org/dist/hbase/{{ site.hbase_basename }}/{{ site.hbase_basename }}-{{ site.hbase_variant }}.tar.gz
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

Now you may start HBase.

```
$ bin/start-hbase.sh
```

Now you have installed everything you need to run PredictionIO!

Next: Reading [Quick Start]({{site.baseurl}}/tutorials/engines/quickstart.html)
