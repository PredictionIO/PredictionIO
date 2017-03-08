#!/bin/bash -ex
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

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

docker pull predictionio/pio-testing-base

pushd $DIR/..

source conf/set_build_profile.sh ${BUILD_PROFILE}
source conf/pio-vendors.sh
if [ ! -f $DIR/docker-files/${PGSQL_JAR} ]; then
  wget $PGSQL_DOWNLOAD
  mv ${PGSQL_JAR} $DIR/docker-files/
fi
if [ ! -f $DIR/docker-files/${SPARK_ARCHIVE} ]; then
  wget $SPARK_DOWNLOAD
  mv $SPARK_ARCHIVE $DIR/docker-files/
fi

if [ -z "$ES_VERSION" ]; then
  ./make-distribution.sh -Dbuild.profile=${BUILD_PROFILE}
else
  ./make-distribution.sh --with-es=$ES_VERSION -Dbuild.profile=${BUILD_PROFILE}
fi
sbt/sbt clean
mkdir assembly
cp dist/lib/*.jar assembly/
mkdir -p lib/spark
if [ -e dist/lib/spark/*.jar ]; then
  cp dist/lib/spark/*.jar lib/spark
fi

docker build -t predictionio/pio .
popd
docker build -t predictionio/pio-testing $DIR \
  --build-arg SPARK_ARCHIVE=$SPARK_ARCHIVE \
  --build-arg SPARK_DIR=$SPARK_DIR \
  --build-arg PGSQL_JAR=$PGSQL_JAR \
  --build-arg BUILD_PROFILE=$BUILD_PROFILE \
  --build-arg PIO_SCALA_VERSION=$PIO_SCALA_VERSION \
  --build-arg PIO_SPARK_VERSION=$PIO_SPARK_VERSION
