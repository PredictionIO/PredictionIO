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

USAGE=$"Usage: run_docker <meta> <event> <model> <command>
  Where:
    meta         = [PGSQL,ELASTICSEARCH]
    event        = [PGSQL,HBASE,ELASTICSEARCH]
    model        = [PGSQL,LOCALFS,HDFS]
    command      = command to run in the container"

if ! [[ "$1" =~ ^(PGSQL|ELASTICSEARCH)$ ]]; then
  echo "$USAGE"
  exit 1
fi
META="$1"
shift

if ! [[ "$1" =~ ^(PGSQL|HBASE|ELASTICSEARCH)$ ]]; then
  echo "$USAGE"
  exit 1
fi
EVENT="$1"
shift

if ! [[ "$1" =~ ^(PGSQL|LOCALFS|HDFS)$ ]]; then
  echo "$USAGE"
  exit 1
fi
MODEL="$1"
shift

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

docker-compose -f $DIR/docker-compose.yml run \
  -e PIO_STORAGE_REPOSITORIES_METADATA_SOURCE=$META \
  -e PIO_STORAGE_REPOSITORIES_EVENTDATA_SOURCE=$EVENT \
  -e PIO_STORAGE_REPOSITORIES_MODELDATA_SOURCE=$MODEL \
  pio-testing $@
