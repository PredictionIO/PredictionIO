#!/usr/bin/env bash

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

SCALA_VERSION=2.10

# Figure out where PredictionIO is installed
FWDIR="$(cd `dirname $0`/..; pwd)"

. ${FWDIR}/bin/load-pio-env.sh

# Build up classpath
CLASSPATH="${PIO_CONF_DIR}"

CLASSPATH="$CLASSPATH:${FWDIR}/plugins/*"

ASSEMBLY_DIR="${FWDIR}/assembly/src/universal/lib"

if [ -n "$JAVA_HOME" ]; then
  JAR_CMD="$JAVA_HOME/bin/jar"
else
  JAR_CMD="jar"
fi

lib_spark_classpath=''
# Use pio-assembly JAR from either RELEASE or assembly directory
if [ -f "${FWDIR}/RELEASE" ]; then
  assembly_folder="${FWDIR}"/lib
  lib_spark_jars=`ls "${FWDIR}"/lib/spark/*.jar`
else
  assembly_folder="${ASSEMBLY_DIR}"
  lib_spark_jars=`ls "${FWDIR}"/assembly/src/universal/lib/spark/*.jar`
fi
# stable classpath for Spark JARs
for J in $lib_spark_jars; do
  lib_spark_classpath="${lib_spark_classpath}:${J}"
done
CLASSPATH="${CLASSPATH}${lib_spark_classpath}"

ASSEMBLY_JAR=$(ls "${assembly_folder}"/pio-assembly*.jar 2>/dev/null)

CLASSPATH="$CLASSPATH:${ASSEMBLY_JAR}"

# Add hadoop conf dir if given -- otherwise FileSystem.*, etc fail ! Note, this
# assumes that there is either a HADOOP_CONF_DIR or YARN_CONF_DIR which hosts
# the configurtion files.
if [ -n "$HADOOP_CONF_DIR" ]; then
  CLASSPATH="$CLASSPATH:$HADOOP_CONF_DIR"
fi
if [ -n "$YARN_CONF_DIR" ]; then
  CLASSPATH="$CLASSPATH:$YARN_CONF_DIR"
fi
if [ -n "$HBASE_CONF_DIR" ]; then
  CLASSPATH="$CLASSPATH:$HBASE_CONF_DIR"
fi
if [ -n "$ES_CONF_DIR" ]; then
  CLASSPATH="$CLASSPATH:$ES_CONF_DIR"
fi
if [ -n "$POSTGRES_JDBC_DRIVER" ]; then
  CLASSPATH="$CLASSPATH:$POSTGRES_JDBC_DRIVER"
fi
if [ -n "$MYSQL_JDBC_DRIVER" ]; then
  CLASSPATH="$CLASSPATH:$MYSQL_JDBC_DRIVER"
fi


echo "$CLASSPATH"