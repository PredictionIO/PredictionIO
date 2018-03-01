---
title: Importing Data in Batch
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

If you have a large amount of data to start with, performing batch import will
be much faster than sending every event over an HTTP connection.

## Preparing Input File

The import tool expects its input to be a file stored either in the local
filesystem or on HDFS. Each line of the file should be a JSON object string
representing an event. For more information about the format of event JSON
object, please refer to [this page](/datacollection/eventapi/#using-event-api).

Shown below is an example that contains 5 events ready to be imported to the
Event Server.

```json
{"event":"buy","entityType":"user","entityId":"3","targetEntityType":"item","targetEntityId":"0","eventTime":"2014-11-21T01:04:14.716Z"}
{"event":"buy","entityType":"user","entityId":"3","targetEntityType":"item","targetEntityId":"1","eventTime":"2014-11-21T01:04:14.722Z"}
{"event":"rate","entityType":"user","entityId":"3","targetEntityType":"item","targetEntityId":"2","properties":{"rating":1.0},"eventTime":"2014-11-21T01:04:14.729Z"}
{"event":"buy","entityType":"user","entityId":"3","targetEntityType":"item","targetEntityId":"7","eventTime":"2014-11-21T01:04:14.735Z"}
{"event":"buy","entityType":"user","entityId":"3","targetEntityType":"item","targetEntityId":"8","eventTime":"2014-11-21T01:04:14.741Z"}
```

WARNING: Please make sure your import file does not contain any empty lines.
Empty lines will be treated as a null object and will return an error during
import.

## Use SDK to Prepare Batch Input File

Some of the Apache PredictionIO SDKs also provides FileExporter
client. You may use them to prepare the JSON file as described above. The
FileExporter creates event in the same way as EventClient except that the events
are written to a JSON file instead of being sent to EventSever. The written JSON
file can then be used by batch import.

<div class="tabs">
  <div data-tab="PHP SDK" data-lang="php">
(coming soon)
<!--
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
-->

  </div>
  <div data-tab="Python SDK" data-lang="python">

```python
import predictionio
from datetime import datetime
import pytz

# Create a FileExporter and specify "my_events.json" as destination file
exporter = predictionio.FileExporter(file_name="my_events.json")

event_properties = {
    "someProperty" : "value1",
    "anotherProperty" : "value2",
    }
# write the events to a file
event_response = exporter.create_event(
    event="my_event",
    entity_type="user",
    entity_id="uid",
    target_entity_type="item",
    target_entity_id="iid",
    properties=event_properties,
    event_time=datetime(2014, 12, 13, 21, 38, 45, 618000, pytz.utc))

# ...

# close the FileExporter when finish writing all events
exporter.close()

```
  </div>
  <div data-tab="Ruby SDK" data-lang="ruby">
(coming soon)
<!--
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
-->

  </div>
  <div data-tab="Java SDK" data-lang="java">
```java
(coming soon)
```
  </div>
</div>



## Import Events from Input File

Importing events from a file can be done easily using the command line
interface. Assuming that `pio` be in your search path, your App ID be `123`, and
the input file `my_events.json` be in your current working directory:

```bash
$ pio import --appid 123 --input my_events.json
```

After a brief while, the tool should return to the console without any error.
Congratulations! You have successfully imported your events.
