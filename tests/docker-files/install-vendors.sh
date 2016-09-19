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

set -e

if [[ "$#" -ne 2 ]]; then
  echo "Usage: install-vendors.sh <vendors_dir> <path to vendors.sh file>"
  exit 1
fi

echo $SPARK_VERSION

VENDORS=$1

SPARK_VERSION_BC=$SPARK_VERSION
HADOOP_VERSION_BC=$HADOOP_VERSION
SPARK_VERSION=$SPARK_VERSION_OLD
HADOOP_VERSION=$HADOOP_VERSION_OLD

source $2

echo "== Installing old Spark $SPARK_VERSION =="
mkdir -p $VENDORS
wget $SPARK_DOWNLOAD
tar zxvfC $SPARK_ARCHIVE $VENDORS/
rm $SPARK_ARCHIVE

SPARK_VERSION=$SPARK_VERSION_BC
HADOOP_VERSION=$HADOOP_VERSION_BC

source $2

echo "== Installing Spark $SPARK_VERSION =="
mkdir -p $VENDORS
wget $SPARK_DOWNLOAD
tar zxvfC $SPARK_ARCHIVE $VENDORS/
rm $SPARK_ARCHIVE

echo "== Installing Elasticsearch =="
wget $ELASTICSEARCH_DOWNLOAD
tar zxvfC $ELASTICSEARCH_ARCHIVE $VENDORS/
rm $ELASTICSEARCH_ARCHIVE

echo "== Installing HBase =="
wget $HBASE_DOWNLOAD
tar zxvfC $HBASE_ARCHIVE $VENDORS/
rm $HBASE_ARCHIVE
