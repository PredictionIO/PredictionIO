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

if [[ $BUILD_TYPE == Unit ]]; then
  # Run license check
  ./tests/check_license.sh

  # Prepare pio environment variables
  set -a
  source conf/pio-env.sh.travis
  set +a

  # Run stylecheck
  sbt scalastyle
  # Run all unit tests
  sbt test

else
  REPO=`pwd`

  ./tests/run_docker.sh $METADATA_REP $EVENTDATA_REP $MODELDATA_REP \
    $REPO 'python3 /tests/pio_tests/tests.py'
fi
