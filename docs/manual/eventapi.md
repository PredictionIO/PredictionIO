---
layout: docs
title: Event API
---

# Collecting Data through Event API

*Event Server* is designed to collect data into PredictionIO in an event-based
style. Once the Event Server is launched, your application can send data to it
through its *Event API* with HTTP requests or with the *EventClient* of
PredictionIO's SDKs.

> All PredictionIO-compliant engines support the data store (i.e. HBase) and
data format used by the Event Server.
> You may also [modify DataSource]({{site.baseurl}}/cookbook/existingdatasource.html) of an
engine to read data directly from your existing data store.


## Launching the Event Server

> Before launching the Event Server, make sure that your event data store
backend is properly configured and is running. By default, PredictionIO uses
HBase, and a quick configuration can be found
[here]({{site.baseurl}}/install/install-linux.html#hbase).

Everything about PredictionIO can be done through the `bin/pio` command.

> For this section, `$PIO_HOME` refers to the location where you have installed
PredictionIO.

```
$ cd $PIO_HOME
$ bin/pio eventserver
```
### Check server status

```
$ curl -i -X GET http://localhost:7070
```

Sample response:

```
HTTP/1.1 200 OK
Server: spray-can/1.2.1
Date: Wed, 10 Sep 2014 22:37:30 GMT
Content-Type: application/json; charset=UTF-8
Content-Length: 18

{"status":"alive"}
```


### Creating Your First Event

You may connect to the Event Server with HTTP request or by using one of many
**PredictionIO SDKs**. You may also use [Bulk Loading]({{site.baseurl}}/bulkloading.html) for
your old data.

The following shows how one can create an event involving a single entity.

<div class="codetabs">
<div data-lang="Raw HTTP">
{% highlight bash %}
$ curl -i -X POST http://localhost:7070/events.json \
-H "Content-Type: application/json" \
-d '{
  "appId" : 4,
  "event" : "my_event",
  "entityType" : "user"
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
{% endhighlight %}
</div>
<div data-lang="PHP SDK">
{% highlight php %}
<?php
  require_once("vendor/autoload.php");

  use predictionio\EventClient;

  $appId = 4;
  $client = new EventClient($appId);
  $response = $client->createEvent(array(
                        'event' => 'my_event',
                        'entityType' => 'user',
                        'entityId' => 'uid',
                        'properties' => array('prop1'=>1,
                                              'prop2'=>'value2',
                                              'prop3'=>array(1,2,3),
                                              'prop4'=>true,
                                              'prop5'=>array('a','b','c'),
                                              'prop6'=>4.56
                                        ),
                        'eventTime' => '2004-12-13T21:39:45.618-07:00'
                       ));
?>
{% endhighlight %}
</div>
<div data-lang="Python SDK">
{% highlight python %}
from predictionio import EventClient
from datetime import datetime
import pytz
client = EventClient(app_id=4, url="http://localhost:7070")

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
{% endhighlight %}
</div>
<div data-lang="Ruby SDK">
{% highlight ruby %}
require 'predictionio'

event_client = PredictionIO::EventClient.new(4)
event_client.create_event('my_event', 'user', 'uid',
                          'eventTime' => '2004-12-13T21:39:45.618-07:00',
                          'properties' => { 'prop1' => 1,
                                            'prop2' => 'value2',
                                            'prop3' => [1, 2, 3],
                                            'prop4' => true,
                                            'prop5' => %w(a b c),
                                            'prop6' => 4.56 })
{% endhighlight %}
</div>
<div data-lang="Java SDK">
{% highlight bash %}
(coming soon)
{% endhighlight %}
</div>
</div>

The following shows how one can create an event involving two entities (with
`targetEntity`).

<div class="codetabs">
<div data-lang="Raw HTTP">
{% highlight bash %}
$ curl -i -X POST http://localhost:7070/events.json \
-H "Content-Type: application/json" \
-d '{
  "appId" : 4,
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
{% endhighlight %}
</div>
<div data-lang="PHP SDK">
{% highlight php %}
<?php
  require_once("vendor/autoload.php");

  use predictionio\EventClient;

  $appId = 4;
  $client = new EventClient($appId);
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
{% endhighlight %}
</div>
<div data-lang="Python SDK">
{% highlight python %}
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
{% endhighlight %}
</div>
<div data-lang="Ruby SDK">
{% highlight ruby %}
require 'predictionio'

event_client = PredictionIO::EventClient.new(4)
event_client.create_event('my_event', 'user', 'uid',
                          'targetEntityType' => 'item',
                          'targetEntityId' => 'iid',
                          'eventTime' => '2004-12-13T21:39:45.618Z',
                          'properties' => { 'someProperty' => 'value1',
                                            'anotherProperty' => 'value2' })
{% endhighlight %}
</div>
<div data-lang="Java SDK">
{% highlight bash %}
(coming soon)
{% endhighlight %}
</div>
</div>


Sample response:

```
HTTP/1.1 201 Created
Server: spray-can/1.2.1
Date: Wed, 10 Sep 2014 22:51:33 GMT
Content-Type: application/json; charset=UTF-8
Content-Length: 41

{"eventId":"AAAABAAAAQDP3-jSlTMGVu0waj8"}
```


## Using Event API

### Event Creation API

The event creation support many commonly used data.

Field | Type | Description
:---- | :----| :-----
`appId` | Integer | App ID for separating your data set between different
        |         |applications.
`event` | String | Name of the event. (Examples: "sign-up", "rate", "view", "buy"). **Note**: All event names start with "$" are reserved and shouldn't be used as your custom event name (eg. "$set").
`entityType` | String | The entity type. It is the namespace of the entityId and
             | | analogous to the table name of a relational database. The
             | | entityId must be unique within same entityType.
`entityId` | String | The entity ID. `entityType-entityId` becomes the unique
           | | identifier of the entity. For example, you may have entityType
           | | named `user`, and different entity IDs, say `1` and `2`. In this
           | | case, `user-1` and `user-2` uniquely identifies | these two
           | | entities.
`targetEntityType` | String | (Optional) The target entity type.
`targetEntityId` | String | (Optional) The target entity ID.
`properties` | JSON | (Optional) See **Note** below.
`eventTime` | String | (Optional) The time of the event. Although Event Server's
            | | current system time and UTC timezone will be used if this is
            | | unspecified, it is highly recommended that this time should be
            | | generated by the client application in order to accurately
            | | record the time of the event.
            | |  Must be in ISO 8601 format (e.g.
            | | `2004-12-13T21:39:45.618Z`, or `2014-09-09T16:17:42.937-08:00`).

#### Note
`properties` can be associated with either an entity or an event.

-   `properties` associated with an entity:

    The following special events are reserved for updating entities and their properties:

    - `"$set"`: set properties of an entity (also implicitly create the entity). To change properties of entity, you simply set the corresponding properties with value again.
    - `"$unset"`: unset properties of an entity. It means treating the specified properties as not existing anymore. Note that the field `properties` cannot be empty.
    - `"$delete"`: delete the entity.

    For example, setting `properties` of `birthday` and `address` for entity `user-1`:

    ```
    {
      "appId" : 4,
      "event" : "$set",
      "entityType" : "user",
      "entityId" : "1",
      "properties" : {
        "birthday" : "1984-10-11",
        "address" : "1234 Street, San Francisco, CA 94107"
      }
    }
    ```

-   `properties` associated with an event:

    For example `user-1` may have a `rate` event on `item-1` with rating value of `4`.

    ```
    {
      "appId" : 4,
      "event" : "rate",
      "entityType" : "user",
      "entityId" : "1",
      "targetEntityType" : "item",
      "targetEntityId" : "1",
      "properties" : {
        "rating" : 4
      }
    }
    ```

## Debugging Recipes

The following APIs are mainly for development or debugging purpose only. They should not be used by real application under normal circumstances and their availabilities are subject to changes.

Replace `<your_eventId>` by a real one in the following.

### Get an Event

```
$ curl -i -X GET http://localhost:7070/events/<your_eventId>.json
```

### Delete an Event

```
$ curl -i -X DELETE http://localhost:7070/events/<your_eventId>.json
```

### Get All Events of an appId

> Use cautiously!

```
$ curl -i -X GET http://localhost:7070/events.json?appId=<your_appId>
```

The `appId` query parameter is mandatory.

In addition, the following *optional* parameters are supported:

- `startTime`: time in ISO8601 format. Return events with `eventTime >= startTime`.
- `untilTime`: time in ISO8601 format. Return events with `eventTime < untilTime`.
- `entityType`: String. The entityType. Return events for this `entityType` only.
- `entityId`: String. The entityId. Return events for this `entityId` only.

> If you are using <code>curl</code> with the <code>&</code> symbol, you should quote the entire URL by using single or double quotes.

For example, get all events of appId with `eventTime >= startTime`

```
$ curl -i -X GET "http://localhost:7070/events.json?appId=<your_appId>&startTime=<time in ISO8601 format>"
```

For example, get all events of an appId with `eventTime < untilTime`:

```
$ curl -i -X GET "http://localhost:7070/events.json?appId=<your_appId>&untilTime=<time in ISO8601 format>"
```

For example, get all events of an appId with `eventTime >= startTime` and `eventTime < untilTime`:

```
$ curl -i -X GET "http://localhost:7070/events.json?appId=<your_appId>&startTime=<time in ISO8601 format>&untilTime=<time in ISO8601 format>"
```

For example, get all events of a specific entity with `eventTime < untilTime`:

```
$ curl -i -X GET "http://localhost:7070/events.json?appId=<your_appId>&entityType=<your_entityType>&entityId=<your_entityId>&untilTime=<time in ISO801 format>"
```

### Delete All Events of an appId

> Use cautiously!

```
$ curl -i -X DELETE http://localhost:7070/events.json?appId=<your_appId>
```
