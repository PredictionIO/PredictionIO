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

# simple test script for dataapi
accessKey=$1

function checkGET () {
  resp=$( curl -i -s -X GET "http://localhost:7070$1" )
  status=$( echo "$resp" | grep HTTP/1.1 )
  exp=$2
  if [[ $status =~ (.*HTTP/1.1 $exp [a-zA-Z]+) ]]; then
  echo "[pass] GET $1 $status"
  else
  echo "[fail] GET $1 $resp"
  echo "expect $exp"
  exit -1
  fi
}


function checkPOST () {
  resp=$( curl -i -s -X POST http://localhost:7070$1 \
  -H "Content-Type: application/json" \
  -d "$2" )
  status=$( echo "$resp" | grep HTTP/1.1 )
  exp=$3
  if [[ $status =~ (.*HTTP/1.1 $exp [a-zA-Z]+) ]]; then
  #echo "POST $1 $2 good $status"
  echo "[pass] POST $1 $status"
  else
  echo "[fail] POST $1 $2 $resp"
  echo "expect $exp"
  exit -1
  fi
}

# ---------------
# status
# ----------------

checkGET "/" 200


# -----------
# reserved events
# ------------

testdata='{
  "event" : "$set",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "properties" : {
    "prop1" : 1,
  }
  "eventTime" : "2004-12-13T21:39:45.618Z"
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 201

testdata='{
  "event" : "$unset",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "properties" : {
    "prop1" : "",
  }
  "eventTime" : "2004-12-13T21:39:45.618Z"
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 201

testdata='{
  "event" : "$delete",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "eventTime" : "2004-12-13T21:39:45.618Z"
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 201

testdata='{
  "event" : "$xxxx",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "targetEntityType" : "my_target_entity_type",
  "targetEntityId" : "my_target_entity_id",
  "properties" : {
    "prop1" : 1,
  }
  "eventTime" : "2004-12-13T21:39:45.618Z"
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 400

# -------------
# create events
# -------------


# full
testdata='{
  "event" : "my_event",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "targetEntityType" : "my_target_entity_type",
  "targetEntityId" : "my_target_entity_id",
  "properties" : {
    "prop1" : 1,
    "prop2" : "value2",
    "prop3" : [1, 2, 3],
    "prop4" : true,
    "prop5" : ["a", "b", "c"],
    "prop6" : 4.56
  }
  "eventTime" : "2004-12-13T21:39:45.618Z"
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 201

# different time zone
testdata='{
  "event" : "my_event_tzone",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "eventTime" : "2004-12-13T21:39:45.618-08:00"
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 201

testdata='{
  "event" : "my_event_tzone",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "eventTime" : "2004-12-13T21:39:45.618+02:00"
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 201

# invalid timezone
testdata='{
  "event" : "my_event_tzone",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "eventTime" : "2004-12-13T21:39:45.618ABC"
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 400


# invalid timezone
testdata='{
  "event" : "my_event_tzone",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "eventTime" : "2004-12-13T21:39:45.618+1"
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 400

# no properties
testdata='{
  "event" : "my_event",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "targetEntityType" : "my_target_entity_type",
  "targetEntityId" : "my_target_entity_id",
  "eventTime" : "2004-12-13T21:39:45.618Z"
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 201

# no properties with $unset event
testdata='{
  "event" : "$unset",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "eventTime" : "2004-12-13T21:39:45.618Z"
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 400

testdata='{
  "event" : "my_event",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "targetEntityType" : "my_target_entity_type",
  "targetEntityId" : "my_target_entity_id",
  "eventTime" : "2004-12-14T21:39:45.618Z",
  "properties": {}
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 201

# no properties with $unset event
testdata='{
  "event" : "$unset",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "targetEntityType" : "my_target_entity_type",
  "targetEntityId" : "my_target_entity_id",
  "eventTime" : "2004-12-14T21:39:45.618Z",
  "properties": {}
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 400

# no tags
testdata='{
  "event" : "my_event",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "targetEntityType" : "my_target_entity_type",
  "targetEntityId" : "my_target_entity_id",
  "properties" : {
    "prop1" : "value1",
    "prop2" : "value2"
  }
  "eventTime" : "2004-12-15T21:39:45.618Z"
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 201

## no eventTIme
testdata='{
  "event" : "my_event",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "targetEntityType" : "my_target_entity_type",
  "targetEntityId" : "my_target_entity_id",
  "properties" : {
    "prop1" : "value1",
    "prop2" : "value2"
  }
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 201

## with prid
testdata='{
  "event" : "my_event",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "targetEntityType" : "my_target_entity_type",
  "targetEntityId" : "my_target_entity_id",
  "properties" : {
    "prop1" : "value1",
    "prop2" : "value2"
  }
  "eventTime" : "2004-12-13T21:39:45.618Z"
  "prId" : "asfasfdsafdcsdFDWd"
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 201

# minimum
testdata='{
  "event" : "my_event",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id"
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 201


# check accepting null for optional fields
testdata='{
  "event" : "my_event",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "targetEntityType" : null,
  "targetEntityId" : null,
  "properties" : null,
  "eventTime" : null
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 201

testdata='{
  "event" : "my_event",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "targetEntityType" : null,
  "targetEntityId" : null,
  "properties" : {
    "prop1": 1,
    "prop2": null
  },
  "eventTime" : null
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 201

# ----------------------------
# create events error cases
# ----------------------------

# missing event
testdata='{
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "targetEntityType" : "my_target_entity_type",
  "targetEntityId" : "my_target_entity_id",
  "properties" : {
    "prop1" : 1,
    "prop2" : "value2",
    "prop3" : [1, 2, 3],
    "prop4" : true,
    "prop5" : ["a", "b", "c"],
    "prop6" : 4.56
  }
  "eventTime" : "2004-12-13T21:39:45.618Z"
}'

# missing entityType
testdata='{
  "event" : "my_event",
  "entityId" : "my_entity_id",
  "targetEntityType" : "my_target_entity_type",
  "targetEntityId" : "my_target_entity_id",
  "properties" : {
    "prop1" : 1,
    "prop2" : "value2",
    "prop3" : [1, 2, 3],
    "prop4" : true,
    "prop5" : ["a", "b", "c"],
    "prop6" : 4.56
  }
  "eventTime" : "2004-12-13T21:39:45.618Z"
}'

# missing entityId
testdata='{
  "event" : "my_event",
  "entityType" : "my_entity_type",
  "properties" : {
    "prop1" : "value1",
    "prop2" : "value2"
  }
  "eventTime" : "2004-12-13T21:39:45.618Z"
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 400

# empty event string
testdata='{
  "event" : "",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "targetEntityType" : "my_target_entity_type",
  "targetEntityId" : "my_target_entity_id",
  "properties" : {
    "prop1" : "value1",
    "prop2" : "value2"
  }
  "eventTime" : "2004-12-13T21:39:45.618Z"
}'

checkPOST "/events.json?accessKey=$accessKey" "$testdata" 400

# empty
testdata='{}'
checkPOST "/events.json?accessKey=$accessKey" "$testdata" 400

# empty
testdata=''
checkPOST "/events.json?accessKey=$accessKey" "$testdata" 400

# invalid data
testdata='asfd'
checkPOST "/events.json?accessKey=$accessKey" "$testdata" 400

# invalid pio_ entityType
testdata='{
  "event" : "my_event",
  "entityType" : "pio_xx",
  "entityId" : "my_entity_id",
  "targetEntityType" : "my_target_entity_type",
  "targetEntityId" : "my_target_entity_id",
  "properties" : {
    "prop1" : 1,
    "prop2" : "value2"
  }
  "eventTime" : "2004-12-13T21:39:45.618Z"
}'
checkPOST "/events.json?accessKey=$accessKey" "$testdata" 400

# invalid pio_ targetEntityType
testdata='{
  "event" : "my_event",
  "entityType" : "food",
  "entityId" : "my_entity_id",
  "targetEntityType" : "pio_xxx",
  "targetEntityId" : "my_target_entity_id",
  "properties" : {
    "prop1" : 1,
    "prop2" : "value2"
  }
  "eventTime" : "2004-12-13T21:39:45.618Z"
}'
checkPOST "/events.json?accessKey=$accessKey" "$testdata" 400

# invalid pio_ properties
testdata='{
  "event" : "my_event",
  "entityType" : "food",
  "entityId" : "my_entity_id",
  "targetEntityType" : "food2",
  "targetEntityId" : "my_target_entity_id",
  "properties" : {
    "pio_aaa" : 1,
    "prop2" : "value2"
  }
  "eventTime" : "2004-12-13T21:39:45.618Z"
}'
checkPOST "/events.json?accessKey=$accessKey" "$testdata" 400

# valid pio_pr entityType
testdata='{
  "event" : "my_event",
  "entityType" : "pio_pr",
  "entityId" : "my_entity_id",
  "targetEntityType" : "my_target_entity_type",
  "targetEntityId" : "my_target_entity_id",
  "properties" : {
    "prop1" : 1,
    "prop2" : "value2"
  }
  "eventTime" : "2004-12-13T21:39:45.618Z"
}'
checkPOST "/events.json?accessKey=$accessKey" "$testdata" 201

# -----
# get events
# ----

checkGET "/events.json?accessKey=$accessKey" 200

# invalid accessKey
checkGET "/events.json?accessKey=999" 401

checkGET "/events.json?accessKey=$accessKey&startTime=abc" 400

checkGET "/events.json?accessKey=$accessKey&untilTime=abc" 400

checkGET "/events.json?accessKey=$accessKey&startTime=2004-12-13T21:39:45.618Z&untilTime=2004-12-15T21:39:45.618Z" 200

# -----
# batch request
# ----

# normal request
testdata='[{
  "event" : "my_event",
  "entityType" : "user",
  "entityId" : "uid",
  "targetEntityType" : "item",
  "targetEntityId" : "iid",
  "properties" : {
    "someProperty" : "value1",
    "anotherProperty" : "value2"
  },
  "eventTime" : "2004-12-13T21:39:45.618Z"
}]'

checkPOST "/batch/events.json?accessKey=$accessKey" "$testdata" 200

# request with a malformed event (2nd event)
# the response code is successful but the error for individual event is reflected in the response's body.
testdata='[{
  "event" : "my_event_1",
  "entityType" : "user",
  "entityId" : "uid",
  "eventTime" : "2004-12-13T21:39:45.618Z"
}, {
  "eve" : "my_event_2",
  "entityType" : "user",
  "entityId" : "uid",
  "eventTime" : "2015-12-13T21:39:45.618Z"
}]'

checkPOST "/batch/events.json?accessKey=$accessKey" "$testdata" 200

# request with too many events (more than 50)
testdata=`cat data/very_long_batch_request.txt`
checkPOST "/batch/events.json?accessKey=$accessKey" "$testdata" 400
