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

# Go to PredictionIO directory
FWDIR="$(cd "`dirname "$0"`"/..; pwd)"
mkdir -p ${FWDIR}/lib
cd ${FWDIR}/lib

# Download RAT jar in lib/
RAT_VERSION=0.11
RAT_JAR="${FWDIR}/lib/apache-rat-${RAT_VERSION}.jar"
URL="http://repo1.maven.org/maven2/org/apache/rat/apache-rat/${RAT_VERSION}/apache-rat-${RAT_VERSION}.jar"
if [ ! -f "$RAT_JAR" ]; then
  if [ $(command -v curl) ]; then
    curl -OL --silent "${URL}"
  elif [ $(command -v wget) ]; then
    wget --quiet ${URL}
  fi
fi
if [ ! -f "$RAT_JAR" ]; then
  echo "${RAT_JAR} download failed. Please install rat manually.\n"
  exit 1
fi

# Run RAT testing
TEST_DIR="${FWDIR}/tests"
REPORT_DIR="${FWDIR}/test-reports"
mkdir ${REPORT_DIR}
java -jar ${RAT_JAR} -E ${TEST_DIR}/.rat-excludes -d ${FWDIR} > ${REPORT_DIR}/rat-results.txt
if [ $? -ne 0 ]; then
 echo "RAT exited abnormally"
 exit 1
fi

# Print results
ERRORS="$(cat ${REPORT_DIR}/rat-results.txt | grep -e "??")"
if test ! -z "$ERRORS"; then
  echo "Could not find Apache license headers in the following files:"
  echo "$ERRORS"
  exit 1
else
  echo -e "RAT checks passed."
fi
