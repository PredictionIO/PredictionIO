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

# Go to PredictionIO directory
FWDIR="$(cd "`dirname "$0"`"/..; pwd)"
cd ${FWDIR}

# Extract information from PIO's build configuration
PIO_VERSION=$(grep ^version ${FWDIR}/build.sbt | grep ThisBuild | grep -o '".*"' | sed 's/"//g')
SCALA_VERSION=$(grep ^scalaVersion ${FWDIR}/build.sbt | grep ThisBuild | grep -o ', ".*"' | sed 's/[, "]//g')
SPARK_VERSION=$(grep ^sparkVersion ${FWDIR}/build.sbt | grep ThisBuild | grep -o ', ".*"' | sed 's/[, "]//g')

echo "======================================================================="
echo "PIO_VERSION: $PIO_VERSION"
echo "SCALA_VERSION: $SCALA_VERSION"
echo "SPARK_VERSION: $SPARK_VERSION"
echo "======================================================================="

"${FWDIR}/sbt/sbt" publishLocal

function check_template(){
  TEMPLATE=$1
  
  cd "${FWDIR}/templates/$TEMPLATE"
  TEMPLATE_PIO_VERSION=$(grep apache-predictionio-core build.sbt | grep -o '".*"' | sed 's/"//g' | awk '{ print $5 }')
  TEMPLATE_SCALA_VERSION=$(grep ^scalaVersion build.sbt | grep -o '".*"' | sed 's/"//g')
  TEMPLATE_SPARK_VERSION=$(grep spark-mllib build.sbt | grep -o '".*"' | sed 's/"//g' | awk '{ print $5 }')

  echo "Checking $TEMPLATE..."
  
  if test "$PIO_VERSION" != "$TEMPLATE_PIO_VERSION" ; then
    echo -e "\033[0;31m[error]\033[0;39m $TEMPLATE: PIO is $PIO_VERSION but template version is $TEMPLATE_PIO_VERSION."
    exit 1
  fi
  if test "$SCALA_VERSION" != "$TEMPLATE_SCALA_VERSION" ; then
    echo -e "\033[0;31m[error]\033[0;39m $TEMPLATE: PIO's Scala version should be $SCALA_VERSION but template version is $TEMPLATE_SCALA_VERSION."
    exit 1
  fi
  if test "$SPARK_VERSION" != "$TEMPLATE_SPARK_VERSION" ; then
    echo -e "\033[0;31m[error]\033[0;39m $TEMPLATE: PIO's Spark version should be $SPARK_VERSION but template version is $TEMPLATE_SPARK_VERSION."
    exit 1
  fi  
  "${FWDIR}/sbt/sbt" clean test
}

# Check templates
check_template template-scala-parallel-recommendation

