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
cd ${FWDIR}

echo "Check library dependencies..."

# Generate license report
sbt/sbt clean
sbt/sbt dumpLicenseReport

sbt/sbt storage/clean
sbt/sbt storage/dumpLicenseReport

# Gather and filter reports
REPORT_DIR="${FWDIR}/test-reports"
mkdir -p ${REPORT_DIR}
find . -name "*-licenses.csv" -exec cat {} >> "${REPORT_DIR}/licences-concat.csv" \;
cat "${REPORT_DIR}/licences-concat.csv" | sort | uniq | grep -v "Category,License,Dependency,Notes" | \
  grep -v Apache | grep -v ASL | \
  grep -v "org.apache" | grep -v "commons-" | \
  grep -v "org.codehaus.jettison" | \
  grep -v predictionio > "${REPORT_DIR}/licences-notice.csv"

# Check undocumented
EXIT_CODE=0
cat "${REPORT_DIR}/licences-notice.csv" | while read LINE
do
  LIBRARY=`echo ${LINE} | cut -d ',' -f 3`
  grep -q "$LIBRARY" LICENSE.txt
  if [ $? -ne 0 ]; then
    echo -e "\033[0;31m[error]\033[0;39m Undocumented dependency: $LINE"
    EXIT_CODE=1
  fi
done

if [ $EXIT_CODE -eq 0 ]; then
  echo "Library checks passed."
else 
  echo "Library checks failed."
fi

exit $EXIT_CODE

