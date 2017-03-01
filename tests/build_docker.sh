#!/bin/bash -x
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

source $DIR/docker-files/env-conf/vendors-env.sh

if [ ! -f $DIR/docker-files/${PGSQL_JAR} ]; then
  wget $PGSQL_DOWNLOAD
  mv ${PGSQL_JAR} $DIR/docker-files/
fi

if [ $BUILD_PROFILE = "scala-2.10" ]; then
  SPARK_DIR=${OLD_SPARK_DIR}
  SPARK_DOWNLOAD=${OLD_SPARK_DOWNLOAD}
  SPARK_ARCHIVE=${OLD_SPARK_ARCHIVE}
fi
if [ ! -f $DIR/docker-files/${SPARK_ARCHIVE} ]; then
  wget $SPARK_DOWNLOAD
  mv $SPARK_ARCHIVE $DIR/docker-files/
fi

docker pull predictionio/pio-testing-base
pushd $DIR/..
./make-distribution.sh -Dbuild.profile=${BUILD_PROFILE}
sbt/sbt clean
mkdir assembly
cp dist/lib/*.jar assembly/
source conf/set_build_profile.sh ${BUILD_PROFILE}
docker build -t predictionio/pio .
popd
docker build -t predictionio/pio-testing $DIR \
  --build-arg SPARK_ARCHIVE=$SPARK_ARCHIVE \
  --build-arg SPARK_DIR=$SPARK_DIR \
  --build-arg PGSQL_JAR=$PGSQL_JAR
