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

echo '== Setting up Postgres... =='
service postgresql start
runuser postgres -c 'createuser -s root'
createdb root

psql -c "create user pio with password 'pio'" && createdb pio

echo '== Starting SSH... =='
service ssh start
ssh-keygen -b 2048 -t rsa -q -f /root/.ssh/id_rsa -N ""
cat /root/.ssh/id_rsa.pub >> /root/.ssh/authorized_keys

echo '== Starting HBase... =='
$HBASE_HOME/bin/start-hbase.sh

echo '== Starting standalone Spark cluster... =='
$SPARK_HOME/sbin/start-all.sh

echo '== Starting Elasticsearch... =='
$ELASTICSEARCH_HOME/bin/elasticsearch -d -p $PIO_HOME/es.pid

echo '== Copying distribution to PIO_HOME... =='
DISTRIBUTION_TAR=`find /pio_host -maxdepth 1 -name PredictionIO-*.tar.gz | head -1`
tar zxvfC $DISTRIBUTION_TAR /
DIR_NAME=/`basename $DISTRIBUTION_TAR`
DIR_NAME=${DIR_NAME%.tar.gz}
mv $DIR_NAME/* $PIO_HOME/
mv /pio-env.sh $PIO_HOME/conf/pio-env.sh

echo '== Copying tests to a separate directory =='
mkdir /tests
cp -r /pio_host/tests/pio_tests /tests/pio_tests
export PYTHONPATH=/tests:$PYTHONPATH

# after initialization run given command
eval $@
