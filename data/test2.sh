

curl -i -X POST "http://localhost:7070/events.json" \
-H "Content-Type: application/json" \
-d '{
  "event" : "$delete",
  "entityType" : "pio_user",
  "entityId" : "123",
  "appId" : 2,
}'


curl -i -X POST "http://localhost:7070/events.json" \
-H "Content-Type: application/json" \
-d '{
  "event" : "$delete",
  "entityType" : "pio_item",
  "entityId" : "174",
  "appId" : 2,
}'


curl -i -X POST "http://localhost:7070/events.json" \
-H "Content-Type: application/json" \
-d '{
  "event" : "$set",
  "entityType" : "pio_item",
  "entityId" : "174",
  "appId" : 4,
  "properties" : {
    "piox_a" : 1
  }
}'


curl -i -X POST "http://localhost:7070/events.json" \
-H "Content-Type: application/json" \
-d '{
  "event" : "my_event",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "targetEntityType" : null,
  "targetEntityId" : null,
  "properties" : {
    "prop1" : 1,
    "prop2" : null,
  }
  "eventTime" : "2004-12-13T21:39:45.618Z",
  "appId" : 4
}'


curl -i -X POST "http://localhost:7070/events.json" \
-H "Content-Type: application/json" \
-d '{
  "event" : "my_event",
  "entityType" : "my_entity_type",
  "entityId" : "my_entity_id",
  "targetEntityType" : null,
  "targetEntityId" : null,
  "properties" : null,
  "eventTime" : null,
  "appId" : 4
}'
