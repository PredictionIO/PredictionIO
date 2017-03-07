#!/bin/bash -x

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

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [ ! -f $DIR/docker-files/spark-1.6.3-bin-hadoop2.6.tgz ]; then
  wget http://d3kbcqa49mib13.cloudfront.net/spark-1.6.3-bin-hadoop2.6.tgz
  mv spark-1.6.3-bin-hadoop2.6.tgz $DIR/docker-files/
fi

if [ ! -f $DIR/docker-files/postgresql-9.4-1204.jdbc41.jar ]; then
  wget https://jdbc.postgresql.org/download/postgresql-9.4-1204.jdbc41.jar
  mv postgresql-9.4-1204.jdbc41.jar $DIR/docker-files/
fi

docker pull predictionio/pio-testing-base
pushd $DIR/..
./make-distribution.sh
sbt/sbt clean
mkdir assembly
cp dist/lib/*.jar assembly/
mkdir -p lib/spark
cp dist/lib/spark/*.jar lib/spark
docker build -t predictionio/pio .
popd
docker build -t predictionio/pio-testing $DIR
