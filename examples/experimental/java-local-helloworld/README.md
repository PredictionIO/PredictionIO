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

# My First "Hello World" Engine

```
$ cd $PIO_HOME/examples/java-local-helloworld
```

Prepare training data:
```
$ cp ../data/helloworld/data1.csv ../data/helloworld/data.csv
```

Register engine:

```
$ ../../bin/pio build

```

Train:

```
$ ../../bin/pio train
```

Example output:

```
2014-08-11 14:29:35,877 INFO  APIDebugWorkflow$ - Metrics is null. Stop here
2014-08-11 14:29:36,099 INFO  APIDebugWorkflow$ - Saved engine instance with ID: 201408110004
```

```
$ ../../bin/pio deploy
```

Retrieve prediction:

```
$ curl -H "Content-Type: application/json" -d '{ "day": "Mon" }' http://localhost:8000/queries.json
```

Output:

```
{"temperature":75.5}
```

Retrieve prediction:

```
$ curl -H "Content-Type: application/json" -d '{ "day": "Tue" }' http://localhost:8000/queries.json
```

Output:
```
{"temperature":80.5}
```


Re-train with new data:

```
$ cd $PIO_HOME/examples/java-local-helloworld
$ cp ../data/helloworld/data2.csv ../data/helloworld/data.csv
```

```
$ ../../bin/pio train
$ ../../bin/pio deploy
```

````
$ curl -H "Content-Type: application/json" -d '{ "day": "Mon" }' http://localhost:8000/queries.json

{"temperature":76.66666666666667}
````
