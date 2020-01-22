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
    PIO_SCALA_VERSION="2.11.12"
fi

if [ -z "$PIO_SPARK_VERSION" ]; then
    PIO_SPARK_VERSION="2.1.3"
fi

if [ -z "$PIO_HADOOP_VERSION" ]; then
    PIO_HADOOP_VERSION="2.7.7"
fi

if [ -z "$PIO_ELASTICSEARCH_VERSION" ]; then
    PIO_ELASTICSEARCH_VERSION="6.8.1"
fi

if [ -z "$PIO_HBASE_VERSION" ]; then
    PIO_HBASE_VERSION="1.2.6"
fi

export ES_IMAGE="docker.elastic.co/elasticsearch/elasticsearch"
export ES_TAG="$PIO_ELASTICSEARCH_VERSION"

HBASE_MAJOR=`echo $PIO_HBASE_VERSION | awk -F. '{print $1 "." $2}'`
export HBASE_TAG="$HBASE_MAJOR"

PGSQL_JAR=postgresql-9.4-1204.jdbc41.jar
PGSQL_DOWNLOAD=https://jdbc.postgresql.org/download/${PGSQL_JAR}

HADOOP_MAJOR=`echo $PIO_HADOOP_VERSION | awk -F. '{print $1 "." $2}'`
SPARK_DIR=spark-${PIO_SPARK_VERSION}-bin-hadoop${HADOOP_MAJOR}
SPARK_ARCHIVE=${SPARK_DIR}.tgz
SPARK_DOWNLOAD_MIRROR=https://www.apache.org/dyn/closer.lua\?action=download\&filename=spark/spark-${PIO_SPARK_VERSION}/${SPARK_ARCHIVE}
SPARK_DOWNLOAD_ARCHIVE=https://archive.apache.org/dist/spark/spark-${PIO_SPARK_VERSION}/${SPARK_ARCHIVE}
