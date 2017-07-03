#!/bin/bash
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

# IMPORTANT: PIO_*_VERSION for dependencies must be set before envoking this script.
# `source conf/set_build_profile.sh $BUILD_PROFILE` to get the proper versions

if [ -z "$PIO_SCALA_VERSION" ]; then
    PIO_SCALA_VERSION="2.11.8"
fi

if [ -z "$PIO_SPARK_VERSION" ]; then
    PIO_SPARK_VERSION="2.1.1"
fi

if [ -z "$PIO_HADOOP_VERSION" ]; then
    PIO_HADOOP_VERSION="2.6.5"
fi

if [ -z "$PIO_ELASTICSEARCH_VERSION" ]; then
    PIO_ELASTICSEARCH_VERSION="5.4.1"
fi

ES_MAJOR=`echo $PIO_ELASTICSEARCH_VERSION | awk -F. '{print $1}'`

if [ "$ES_MAJOR" = "1" ]; then
    export ES_IMAGE="elasticsearch"
    export ES_TAG="1"
else
    export ES_IMAGE="docker.elastic.co/elasticsearch/elasticsearch"
    export ES_TAG="5.4.1"
fi

PGSQL_JAR=postgresql-9.4-1204.jdbc41.jar
PGSQL_DOWNLOAD=https://jdbc.postgresql.org/download/${PGSQL_JAR}

HADOOP_MAJOR=`echo $PIO_HADOOP_VERSION | awk -F. '{print $1 "." $2}'`
SPARK_DIR=spark-${PIO_SPARK_VERSION}-bin-hadoop${HADOOP_MAJOR}
SPARK_ARCHIVE=${SPARK_DIR}.tgz
SPARK_DOWNLOAD=http://d3kbcqa49mib13.cloudfront.net/${SPARK_ARCHIVE}
# ELASTICSEARCH_DOWNLOAD
#   5.x https://download.elasticsearch.org/elasticsearch/elasticsearch/elasticsearch-${PIO_ELASTICSEARCH_VERSION}.tar.gz
#   1.x https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-${PIO_ELASTICSEARCH_VERSION}.tar.gz
