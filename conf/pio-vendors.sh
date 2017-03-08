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

PGSQL_JAR=postgresql-9.4-1204.jdbc41.jar
PGSQL_DOWNLOAD=https://jdbc.postgresql.org/download/${PGSQL_JAR}

HADOOP_MAJOR=`echo $PIO_HADOOP_VERSION | awk -F. '{print $1 "." $2}'`
SPARK_DIR=spark-${PIO_SPARK_VERSION}-bin-hadoop${HADOOP_MAJOR}
SPARK_ARCHIVE=${SPARK_DIR}.tgz
SPARK_DOWNLOAD=http://d3kbcqa49mib13.cloudfront.net/${SPARK_ARCHIVE}
# ELASTICSEARCH_DOWNLOAD
#   5.x https://download.elasticsearch.org/elasticsearch/elasticsearch/elasticsearch-${PIO_ELASTICSEARCH_VERSION}.tar.gz
#   1.x https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-${PIO_ELASTICSEARCH_VERSION}.tar.gz
