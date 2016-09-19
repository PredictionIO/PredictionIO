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

if [[ "$#" -ne 1 ]]; then
  echo "Usage: docker-build.sh <image-name>"
  exit 1
fi

cd `dirname $(dirname $0)`

source ./dev/set-build-profile.sh scala-2.10
SPARK_VERSION_OLD=$SPARK_VERSION
HADOOP_VERSION_OLD=$HADOOP_VERSION

source ./dev/set-build-profile.sh scala-2.11

HBASE_VERSION=1.0.0

cp ./conf/vendors.sh ./tests/docker-files/

docker build -t $1 ./tests \
  --build-arg SPARK_VERSION=$SPARK_VERSION \
  --build-arg SPARK_VERSION_OLD=$SPARK_VERSION_OLD \
  --build-arg HADOOP_VERSION=$HADOOP_VERSION \
  --build-arg HADOOP_VERSION_OLD=$HADOOP_VERSION_OLD \
  --build-arg ELASTICSEARCH_VERSION=$ELASTICSEARCH_VERSION \
  --build-arg HBASE_VERSION=$HBASE_VERSION

rm -fr ./tests/docker-files/vendors.sh
