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

# This script retries the provided command $RETRIES_NUMBER times (3 by default).

if [ -n $RETRIES_NUMBER ]; then
  RETRIES_NUMBER=3
fi

COMMAND_RESULT=1
n=0
until [ $COMMAND_RESULT -eq 0 ] || [ $n -eq $RETRIES_NUMBER ]; do
  echo -- $((n+1))/$RETRIES_NUMBER Trying cmd: \""$@"\"
  eval $@
  COMMAND_RESULT=$?
  n=$[$n+1]
done

if [ $COMMAND_RESULT -ne 0 ]; then
  echo -- Cmd \""$@"\" failed $RETRIES_NUMBER times!
fi

exit $COMMAND_RESULT
