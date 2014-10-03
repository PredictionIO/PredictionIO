---
layout: docs
title: Installation
---

# Installing PredictionIO

To get started, install PredictionIO Server and choose a SDK for your
application.

## Prerequisites

It is **very important** to meet the minimum version of the following
technologies that power PredictionIO.

* Apache Hadoop 2.4.0 (required only if YARN and HDFS are needed)
* Apache HBase 0.98.6
* Apache Spark 1.1.0 for Hadoop 2.4
* Elasticsearch 1.3.0
* Java 7

> **Notes for CentOS 7+ Users**

> If you are using CentOS 7+, Apache Spark local mode, which is the default
operation mode without further configuration, may not work. In that case,
configure your Apache Spark to run in [standalone cluster
mode](http://spark.apache.org/docs/latest/spark-standalone.html).

## Installing PredictionIO Server

PredictionIO runs on a Java virtual machine, so it runs on most platforms.
Choose your platform below:

[//]: # (* Deploying PredictionIO on Amazon Web Services)
* [Installing PredictionIO on Linux](install-linux.html)
* [Installing PredictionIO from Source Code](install-sourcecode.html)

[//]: # (You may also use one of the community-contributed packages to install PredictionIO:)

[//]: # (* *(coming soon)* Installing PredictionIO with Docker)
[//]: # (* *(coming soon)* Installing PredictionIO with Vagrant (VirtualBox))
[//]: # (* *(coming soon)* Installing PredictionIO with Homebrew)

> **Upgrade Note**

> Upgrade instructions from 0.7 to 0.8 is coming soon.


## Production Deployment

For production environment setup, please refer to [Production
Deployment]({{site.baseurl}}/production/deploy.html) guide.
