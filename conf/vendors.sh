#!/bin/bash -
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Assuming that proper environment variables are set, that is:
# HADOOP_VERSION, SPARK_VERSION, ELASTICSEARCH_VERSION, HBASE_VERSION
# this script sets variables in the following form.
# For every vendor, it creates:
# <VENDOR>_DOWNLOAD - link to download the file
# <VENDOR>_ARCHIVE - name of the downloaded archive
# <VENDOR>_DIRNAME - name of the extracted directory

HADOOP_MINOR=`echo $HADOOP_VERSION | awk -F. '{print $1 "." $2}'`
SPARK_DOWNLOAD=http://d3kbcqa49mib13.cloudfront.net/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_MINOR}.tgz
SPARK_ARCHIVE=spark-${SPARK_VERSION}-bin-hadoop${HADOOP_MINOR}.tgz
SPARK_DIRNAME=spark-${SPARK_VERSION}-bin-hadoop${HADOOP_MINOR}

ELASTICSEARCH_DOWNLOAD=https://download.elasticsearch.org/elasticsearch/elasticsearch/elasticsearch-${ELASTICSEARCH_VERSION}.tar.gz
ELASTICSEARCH_ARCHIVE=elasticsearch-${ELASTICSEARCH_VERSION}.tar.gz
ELASTICSEARCH_DIRNAME=elasticsearch-${ELASTICSEARCH_VERSION}

HBASE_DOWNLOAD=http://archive.apache.org/dist/hbase/hbase-${HBASE_VERSION}/hbase-${HBASE_VERSION}-bin.tar.gz
HBASE_ARCHIVE=hbase-${HBASE_VERSION}-bin.tar.gz
HBASE_DIRNAME=hbase-${HBASE_VERSION}
