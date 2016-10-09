<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

---
title: System Architecture and Dependencies
---

During the [installation](/install), you have installed the following
software:

* Apache Hadoop 2.4.0 (required only if YARN and HDFS are needed)
* Apache HBase 0.98.6
* Apache Spark 1.2.0 for Hadoop 2.4
* Elasticsearch 1.4.0

This section explains how they are used in PredictionIO.

![PredictionIO Systems](/images/0.8-engine-data-pipeline.png)

**HBase**: Event Server uses Apache HBase as the data store. It stores imported
events. If you are not using the PredictionIO Event Server, you do not need to
install HBase.

**Apache Spark**: Spark is a large-scale data processing engine that powers the
algorithm, training, and serving processing.

A spark algorithm is different from conventional single machine algorithm in a way that spark algorithms use the [RDD](http://spark.apache.org/docs/1.0.1/programming-guide.html#resilient-distributed-datasets-rdds) abstraction as its primary data type. PredictionIO framework natively support both RDD-based algorithms and traditional single-machine algorithms.


**HDFS**: The output of training has two parts: a model and its meta-data. The
model is then stored in HDFS or a local file system.

**Elasticsearch**: It stores metadata such as model versions, engine versions, access key and app id mappings, evaluation results, etc.
