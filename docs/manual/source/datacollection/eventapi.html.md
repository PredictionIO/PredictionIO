---
title: Collecting Data through REST/SDKs
---

<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

**Event Server** is designed to collect data into Apache PredictionIO
in an event-based style. Once the Event Server is launched, your
application can send data to it through its **Event API** with HTTP requests or
with `EventClient`s of PredictionIO's SDKs.

INFO: All Apache PredictionIO-compliant engines support accessing
the Event Store (i.e. the data store of Event Server) through [Apache
PredictionIO's Storage
API](http://predictionio.apache.org/api/current/index.html#org.apache.predictionio.data.storage.package).

## Launching the Event Server

INFO: Before launching the Event Server, make sure that your event data store
backend is properly configured and is running. By default, Apache PredictionIO
uses Apache HBase, and a quick configuration can be found
[here](/install/install-sourcecode/#hbase). Please allow a minute (usually less
than 30 seconds) after HBase is started for its initialization to complete
before starting the Event Server.


Everything about Apache PredictionIO can be done through the `pio`
command. Please add PIO binary command path to to your `PATH` first. Assuming
PredictionIO is installed at `/home/yourname/PredictionIO/`, you can run

```
$ PATH=$PATH:/home/yourname/PredictionIO/bin; export PATH
```

To start the event server, run

```
$ pio eventserver
```

INFO: By default, the Event Server is bound to 0.0.0.0, which serves global
traffic. To tighten security, you may use `pio eventserver --ip 127.0.0.1` to
serve only local traffic.

### Check Server Status

```
$ curl -i -X GET http://localhost:7070
```

Sample response:

```
HTTP/1.1 200 OK
Server: akka-http/10.1.5
Date: Wed, 10 Sep 2014 22:37:30 GMT
Content-Type: application/json; charset=UTF-8
Content-Length: 18

{"status":"alive"}
```


### Generating App ID and Access Key

First, you need to create a new app in the Event Server. You will later send data into it.

```
$ pio app new MyTestApp
```

> You can replace `MyTestApp` with name of your App.

Take note of the *Access Key* and *App ID* generated. You need the *Access Key*
to use the Event API. You should see something like the following output:

```
[INFO] [App$] Created new app:
[INFO] [App$]         Name: MyTestApp
[INFO] [App$]           ID: 6
[INFO] [App$]   Access Key: WPgcXKd42FPQpZHVbVeMyqF4CQJUnXQmIMTHhX3ZUrSzvy1KXJjdFUrslifa9rnB
```

### Creating Your First Event

You may connect to the Event Server with HTTP request or by using one of many
**Apache PredictionIO SDKs**.

For example, the following shows how one can create an event involving a single entity.
Replace the value of `accessKey` by the *Access Key* generated for your App.

<div class="tabs">
  <div data-tab="Raw HTTP" data-lang="bash">
```bash
$ curl -i -X POST http://localhost:7070/events.json?accessKey=WPgcXKd42FPQpZHVbVeMyqF4CQJUnXQmIMTHhX3ZUrSzvy1KXJjdFUrslifa9rnB \
-H "Content-Type: application/json" \
-d '{
  "event" : "my_event",
  "entityType" : "user",
  "entityId" : "uid",
  "properties" : {
    "prop1" : 1,
    "prop2" : "value2",
    "prop3" : [1, 2, 3],
    "prop4" : true,
    "prop5" : ["a", "b", "c"],
    "prop6" : 4.56
  }
  "eventTime" : "2004-12-13T21:39:45.618-07:00"
}'
```
  </div>
  <div data-tab="PHP SDK" data-lang="php">
```php
<?php
  require_once("vendor/autoload.php");

  use predictionio\EventClient;

  $accessKey = 'YOUR_ACCESS_KEY';
  $client = new EventClient($accessKey);
  $response = $client->createEvent(array(
                        'event' => 'my_event',
                        'entityType' => 'user',
                        'entityId' => 'uid',
                        'properties' => array('prop1' => 1,
                                              'prop2' => 'value2',
                                              'prop3' => array(1,2,3),
                                              'prop4' => true,
                                              'prop5' => array('a','b','c'),
                                              'prop6' => 4.56
                                        ),
                        'eventTime' => '2004-12-13T21:39:45.618-07:00'
                       ));
?>
```
  </div>
  <div data-tab="Python SDK" data-lang="python">
```python
from predictionio import EventClient
from datetime import datetime
import pytz
client = EventClient('YOUR_ACCESS_KEY', "http://localhost:7070")

first_event_properties = {
    "prop1" : 1,
    "prop2" : "value2",
    "prop3" : [1, 2, 3],
    "prop4" : True,
    "prop5" : ["a", "b", "c"],
    "prop6" : 4.56 ,
    }
first_event_time = datetime(
  2004, 12, 13, 21, 39, 45, 618000, pytz.timezone('US/Mountain'))
first_event_response = client.create_event(
    event="my_event",
    entity_type="user",
    entity_id="uid",
    properties=first_event_properties,
    event_time=first_event_time,
)
```
  </div>
  <div data-tab="Ruby SDK" data-lang="ruby">
```ruby
require 'predictionio'

event_client = PredictionIO::EventClient.new('YOUR_ACCESS_KEY')
event_client.create_event('my_event', 'user', 'uid',
                          'eventTime' => '2004-12-13T21:39:45.618-07:00',
                          'properties' => { 'prop1' => 1,
                                            'prop2' => 'value2',
                                            'prop3' => [1, 2, 3],
                                            'prop4' => true,
                                            'prop5' => %w(a b c),
                                            'prop6' => 4.56 })
```
  </div>
  <div data-tab="Java SDK" data-lang="java">
```java
(coming soon)
```
  </div>
</div>

For example, the following shows how one can create an event involving two entities (with
`targetEntity`).

<div class="tabs">
  <div data-tab="Raw HTTP" data-lang="bash">
```bash
$ curl -i -X POST http://localhost:7070/events.json?accessKey=WPgcXKd42FPQpZHVbVeMyqF4CQJUnXQmIMTHhX3ZUrSzvy1KXJjdFUrslifa9rnB \
-H "Content-Type: application/json" \
-d '{
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
}'
```
  </div>
  <div data-tab="PHP SDK" data-lang="php">
```php
<?php
  require_once("vendor/autoload.php");

  use predictionio\EventClient;

  $accessKey = 'YOUR_ACCESS_KEY';
  $client = new EventClient($accessKey);
  $response = $client->createEvent(array(
                        'event' => 'my_event',
                        'entityType' => 'user',
                        'entityId' => 'uid',
                        'targetEntityType' => 'item',
                        'targetEntityId' => 'iid',
                        'properties' => array('someProperty'=>'value1',
                                              'anotherProperty'=>'value2'),
                        'eventTime' => '2004-12-13T21:39:45.618Z'
                       ));
?>
```
  </div>
  <div data-tab="Python SDK" data-lang="python">
```python
# Second Event
second_event_properties = {
    "someProperty" : "value1",
    "anotherProperty" : "value2",
    }
second_event_response = client.create_event(
    event="my_event",
    entity_type="user",
    entity_id="uid",
    target_entity_type="item",
    target_entity_id="iid",
    properties=second_event_properties,
    event_time=datetime(2014, 12, 13, 21, 38, 45, 618000, pytz.utc))
```
  </div>
  <div data-tab="Ruby SDK" data-lang="ruby">
```ruby
require 'predictionio'

event_client = PredictionIO::EventClient.new('YOUR_ACCESS_KEY')
event_client.create_event('my_event', 'user', 'uid',
                          'targetEntityType' => 'item',
                          'targetEntityId' => 'iid',
                          'eventTime' => '2004-12-13T21:39:45.618Z',
                          'properties' => { 'someProperty' => 'value1',
                                            'anotherProperty' => 'value2' })
```
  </div>
  <div data-tab="Java SDK" data-lang="java">
```java
(coming soon)
```
  </div>
</div>


Sample response:

```
HTTP/1.1 201 Created
Server: akka-http/10.1.5
Date: Wed, 10 Sep 2014 22:51:33 GMT
Content-Type: application/json; charset=UTF-8
Content-Length: 41

{"eventId":"AAAABAAAAQDP3-jSlTMGVu0waj8"}
```


## Using Event API

### Event Creation API

URL: `http://localhost:7070/events.json?accessKey=yourAccessKeyString`

Query parameters:

Field | Type | Description
:---- | :----| :-----
`accessKey` | String | The Access Key for your App

The event creation support many commonly used data. POST request body:

Field | Type | Description
:---- | :----| :-----
`event` | String | Name of the event.
        | | (Examples: "sign-up", "rate", "view", "buy").
        | | **Note**: All event names start with "$" and "pio_" are reserved
        | | and shouldn't be used as your custom event name (eg. "$set").
`entityType` | String | The entity type. It is the namespace of the entityId and
             | | analogous to the table name of a relational database. The
             | | entityId must be unique within same entityType.
             | | **Note**: All entityType names start with "$" and "pio_" are
             | | reserved and shouldn't be used.
`entityId` | String | The entity ID. `entityType-entityId` becomes the unique
           | | identifier of the entity. For example, you may have entityType
           | | named `user`, and different entity IDs, say `1` and `2`. In this
           | | case, `user-1` and `user-2` uniquely identifies | these two
           | | entities.
`targetEntityType` | String | (Optional) The target entity type.
                   | | **Note**: All entityType names start with "$" and "pio_"
                   | | are reserved and shouldn't be used.
`targetEntityId` | String | (Optional) The target entity ID.
`properties` | JSON | (Optional) See **Note About Properties** below
             | | **Note**: All property names start with "$" and "pio_"
             | | are reserved and shouldn't be used as keys inside `properties`.
`eventTime` | String | (Optional) The time of the event. Although Event Server's
            | | current system time and UTC timezone will be used if this is
            | | unspecified, it is highly recommended that this time should be
            | | generated by the client application in order to accurately
            | | record the time of the event.
            | |  Must be in ISO 8601 format (e.g.
            | | `2004-12-13T21:39:45.618Z`, or `2014-09-09T16:17:42.937-08:00`).

## Note About Properties

Note that `properties` can be:

1. Associated with an *generic event*: The `properties` field provide additional information about this event
2. Associated with an *entity*: The `properties` field is used to record the changes of an entity's properties with special events `$set`, `$unset` and `$delete`.

Please see the [Events Modeling](/datacollection/eventmodel/) for detailed explanation.


## Debugging Recipes

WARNING: The following API are mainly for development or debugging purpose
only. They should not be supported by SDK nor used by real application under
normal circumstances and they are subject to changes.

INFO: Instead of using `curl`, you can also install JSON browser plugins such as **JSONView** to pretty-print the JSON on your browser. With the browser plugin you can make the `GET` queries below by passing in the URL. Plugins like **Postman - REST Client** provide a more advanced interface for making queries.

The `accessKey` query parameter is mandatory.

Replace `<your_accessKey>` and `<your_eventId>` by a real one in the following:

### Get an Event

```
$ curl -i -X GET http://localhost:7070/events/<your_eventId>.json?accessKey=<your_accessKey>
```

### Delete an Event

```
$ curl -i -X DELETE http://localhost:7070/events/<your_eventId>.json?accessKey=<your_accessKey>
```

### Get Events of an App

```
$ curl -i -X GET http://localhost:7070/events.json?accessKey=<your_accessKey>
```

INFO: By default, it returns at most 20 events. Use the `limit` parameter to specify how many events returned (see below). Use cautiously!

In addition, the following *optional* parameters are supported:

- `startTime`: time in ISO8601 format. Return events with `eventTime >= startTime`.
- `untilTime`: time in ISO8601 format. Return events with `eventTime < untilTime`.
- `entityType`: String. The entityType. Return events for this `entityType` only.
- `entityId`: String. The entityId. Return events for this `entityId` only.
- `event`: String. The event name. Return events with this name only.
- `targetEntityType`: String. The targetEntityType. Return events for this `targetEntityType` only.
- `targetEntityId`: String. The targetEntityId. Return events for this `targetEntityId` only.
- `limit`: Integer. The number of record events returned. Default is 20. -1 to
  get all.
- `reversed`: Boolean. **Must be used with both `entityType` and `entityId` specified**, returns events in reversed chronological order. Default is false.

WARNING: If you are using <code>curl</code> with the <code>&</code> symbol, you should quote the entire URL by using single or double quotes.

WARNING: Depending on the size of data, you may encounter timeout when querying with some of the above filters. Event server uses `entityType` and `entityId` as the key so any query without both `entityType` and `entityId` specified might result in a timeout.

For example, get all events of an app with `eventTime >= startTime`

```
$ curl -i -X GET "http://localhost:7070/events.json?accessKey=<your_accessKey>&startTime=<time in ISO8601 format>"
```

For example, get all events of an app with `eventTime < untilTime`:

```
$ curl -i -X GET "http://localhost:7070/events.json?accessKey=<your_accessKey>&untilTime=<time in ISO8601 format>"
```

For example, get all events of an app with `eventTime >= startTime` and `eventTime < untilTime`:

```
$ curl -i -X GET "http://localhost:7070/events.json?accessKey=<your_accessKey>&startTime=<time in ISO8601 format>&untilTime=<time in ISO8601 format>"
```

For example, get all events of a specific entity with `eventTime < untilTime`:

```
$ curl -i -X GET "http://localhost:7070/events.json?accessKey=<your_accessKey>&entityType=<your_entityType>&entityId=<your_entityId>&untilTime=<time in ISO801 format>"
```

### Delete All Events of an App

Please use the following CLI command:

```
$ pio app data-delete <your_app_name>
```

INFO: See [here](/cli/#event-server-commands) to know details of command-line interface for the event server.
