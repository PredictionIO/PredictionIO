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

#set -x

my_dir="$(dirname "$0")"
filename="$(basename "$0" .sh)"

source "${my_dir}/local.sh"

###############################################################################
# Configuration - read and modify this section.
###############################################################################

# Rename this file to a more descriptive one, it will be used as email title
# with underscore replaced by space. e.g. EngineX_Redeploy_(dev).sh
# This will also make deploying the wrong script less likely.

TIMESTAMP=`date +"%Y%m%d-%H%M%S"`

# LOG_DIR is set in local.sh
LOG_FILE="${LOG_DIR}/${filename}-${TIMESTAMP}.log"

# For accessing the engine server status page
HOSTNAME= #

# A port other than the default 8000 is recommend since it can be shut down
# upon the command "pio deploy" without the "--port" parameter by mistake
PORT= # 8001

# Access key for feedback loop. It is a 64-char string of the form
# abcdefghijklmnopqrstuvwxyz1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ12
ACCESSKEY=

ENGINE_JSON= # enginex_algo_dev.json, enginey_null_prod.json

# Use empty string if you want a local cluster for train, i.e. TRAIN_MASTER=
TRAIN_MASTER="spark://`hostname`:7077"
# Use empty string if you want a local cluster for deploy, i.e. DEPLOY_MASTER=
# For LAlgorithm and P2LAlgorithm, leave this as empty string to avoid holding
# up resources on the spark cluster.
DEPLOY_MASTER=

# Bump these up as needed
TRAIN_EXECUTOR_MEMORY=16G
TRAIN_DRIVER_MEMORY=8G
TRAIN_CORES=4
# 
DEPLOY_EXECUTOR_MEMORY=16G
DEPLOY_DRIVER_MEMORY=8G
DEPLOY_CORES=4

###############################################################################
# End of configuration
###############################################################################


###############
# Sanity check
############### 

NAME=${filename//_/ } # Title of the email

check_non_empty "${NAME/redeploy/}" \
                "a more descriptive script name, e.g. EngineX_Redeploy_(dev).sh"

check_non_empty "$HOSTNAME"    "HOSTNAME"
check_non_empty "$PORT"        "PORT"
check_non_empty "$ACCESSKEY"   "ACCESSKEY"
check_non_empty "$ENGINE_JSON" "ENGINE_JSON"

##################
# Start of script
##################

if [[ "$TRAIN_MASTER" == "" ]]; then 
  TRAIN_MASTER_PARAM=
else
  TRAIN_MASTER_PARAM="--master $TRAIN_MASTER"
fi

TRAIN_COMMAND="${PIO_HOME}/bin/pio train --verbose -v $ENGINE_JSON 
    -- $TRAIN_MASTER_PARAM 
    --executor-memory $TRAIN_EXECUTOR_MEMORY 
    --driver-memory $TRAIN_DRIVER_MEMORY 
    --total-executor-cores $TRAIN_CORES"

if [[ "$DEPLOY_MASTER" == "" ]]; then 
  DEPLOY_MASTER_PARAM=
else
  DEPLOY_MASTER_PARAM="--master $DEPLOY_MASTER"
fi

DEPLOY_COMMAND="${PIO_HOME}/bin/pio deploy -v $ENGINE_JSON
    --ip $IP
    --port $PORT
    --event-server-port $EVENT_SERVER_PORT
    --feedback --accesskey $ACCESSKEY
    -- $DEPLOY_MASTER_PARAM
    --executor-memory $DEPLOY_EXECUTOR_MEMORY
    --driver-memory $DEPLOY_DRIVER_MEMORY
    --total-executor-cores $DEPLOY_CORES"

# Print the two commands then quit if we see any arguments (debug)
if [[ "$1" != "" ]]; then
  echo "[DEBUG] Commands to be run (not actually running it):"
  echo "$(echo $TRAIN_COMMAND | sed "s/\\n//g")"
  echo "$(echo $DEPLOY_COMMAND | sed "s/\\n//g")"
  exit -1
fi

######################
# Actually running it
######################

echo "Logging to $LOG_FILE"
touch $LOG_FILE

echo -n "Deploy script started at " | tee -a $LOG_FILE
date | tee -a $LOG_FILE

# Assume this script is in scripts/ directory of the project
cd $my_dir/..

echo "Running $(echo $TRAIN_COMMAND | sed "s/\\n//g")" | tee -a $LOG_FILE
PIO_TRAIN=$($TRAIN_COMMAND 2>&1)
TRAIN_RESULT=$?

echo "$PIO_TRAIN" >> $LOG_FILE
echo -n "Training ended with return value $TRAIN_RESULT at " | tee -a $LOG_FILE
date | tee -a $LOG_FILE

if [[ $TRAIN_RESULT -ne 0 ]]; then
  mail -s "Error in train: $NAME $TIMESTAMP" -a "From: $FROM_EMAIL" \
      $TARGET_EMAIL < $LOG_FILE
  echo -n "Deploy script aborted at "
  date
  exit 1
fi

# Deploy
# Get current running instance PID
PIDBYPORT_COMMAND="lsof -t -i:$PORT"
DEPLOYEDPID=$($PIDBYPORT_COMMAND)

DEPLOY_LOG=`mktemp $LOG_DIR/tmp.XXXXXXXXXX`
$($DEPLOY_COMMAND 1>$DEPLOY_LOG 2>&1) &

# Check if the engine is up
sleep 60
curl $HOSTNAME:$PORT > /dev/null
RETURN_VAL=$?
COUNTER=0
while [[ $RETURN_VAL -ne 0 && $COUNTER -lt 20 ]]; do
  sleep 30
  curl $HOSTNAME:$PORT > /dev/null
  let RETURN_VAL=$?
  let COUNTER=COUNTER+1
done

# Check if the previous engine instance is running
KILLSD_COMMAND="kill $DEPLOYEDPID"
if [ -z "$DEPLOYEDPID" ]
then
  printf "\nNo stale PIDs found for port $PORT\n"
else
  $($KILLSD_COMMAND)
  printf "\nStale PID found as $DEPLOYEDPID. Resources released.\n"
fi

cat $DEPLOY_LOG >> $LOG_FILE
rm $DEPLOY_LOG
echo -n "Deploy ended with return value $TRAIN_RESULT at " | tee -a $LOG_FILE
date | tee -a $LOG_FILE

if [[ $RETURN_VAL -ne 0 ]]; then
  mail -s "Error in deploy: $NAME $TIMESTAMP" -a "From: $FROM_EMAIL" $TARGET_EMAIL < $LOG_FILE
else
  mail -s "Normal: $NAME $TIMESTAMP" -a "From: $FROM_EMAIL" $TARGET_EMAIL < $LOG_FILE
fi

echo -n "Deploy script ended at "
date
exit $RETURN_VAL
