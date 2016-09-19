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

HBASE_VERSION=1.0.0

if [[ $BUILD_TYPE == Unit ]]; then
  # Download spark, hbase
  mkdir vendors
  set -a
  source dev/set-build-profile.sh $BUILD_PROFILE
  source conf/vendors.sh
  set +a

  dev/retry_command.sh wget $SPARK_DOWNLOAD
  tar zxfC $SPARK_ARCHIVE vendors
  export SPARK_HOME=`pwd`/vendors/$SPARK_DIRNAME

  dev/retry_command.sh wget $HBASE_DOWNLOAD
  tar zxfC $HBASE_ARCHIVE vendors
  export HBASE_HOME=`pwd`/vendors/$HBASE_DIRNAME
  # Prepare pio environment variables
  set -a
  source conf/pio-env.sh.travis
  set +a

  # Create postgres database for PredictionIO
  psql -c 'create database predictionio;' -U postgres
  ./bin/travis/pio-start-travis

else # Integration Tests
  dev/retry_command.sh ./make-distribution.sh -Dbuild.profile=$BUILD_PROFILE
fi
