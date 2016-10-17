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

USAGE=$"Usage: run_docer <meta> <event> <model> <pio> <command>
  Where:
    meta         = [PGSQL,ELASTICSEARCH]
    event        = [PGSQL,HBASE]
    model        = [PGSQL,LOCALFS,HDFS]
    pio          = path to PredictionIO directory
    command      = command to run in the container"

if ! [[ "$1" =~ ^(PGSQL|ELASTICSEARCH)$ ]]; then
  echo "$USAGE"
  exit 1
fi

if ! [[ "$2" =~ ^(PGSQL|HBASE)$ ]]; then
  echo "$USAGE"
  exit 1
fi

if ! [[ "$3" =~ ^(PGSQL|LOCALFS|HDFS)$ ]]; then
  echo "$USAGE"
  exit 1
fi

if [ ! -d "$4" ]; then
  echo "Directory $4 does not exist"
  echo "$USAGE"
  exit 1
fi

docker run -it -h localhost \
  -v $4:/pio_host \
  -v ~/.ivy2:/root/.ivy2 \
  -e PIO_STORAGE_REPOSITORIES_METADATA_SOURCE=$1 \
  -e PIO_STORAGE_REPOSITORIES_EVENTDATA_SOURCE=$2 \
  -e PIO_STORAGE_REPOSITORIES_MODELDATA_SOURCE=$3 \
  -p 8000:8000 -p 7070:7070 -p 8080:8080 -p 8081:8081 -p 4040:4040 \
  -p 60000:60000 -p 60010:60010 -p 60020:60020 -p 60030:60030 predictionio/pio-testing $5
