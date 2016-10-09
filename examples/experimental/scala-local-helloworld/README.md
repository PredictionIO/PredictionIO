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

Prepare training data:
```
$ cd $PIO_HOME/examples/scala-local-helloworld
$ cp ../data/helloworld/data1.csv ../data/helloworld/data.csv
```

Build engine:

```
$ ../../bin/pio build
```

Train:

```
$ ../../bin/pio train
```

Example output:

```
2014-08-05 17:06:02,638 INFO  APIDebugWorkflow$ - Metrics is null. Stop here
2014-08-05 17:06:02,769 INFO  APIDebugWorkflow$ - Run information saved with ID: 201408050005
```

Deploy:

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

## 4. Re-training

Re-train with new data:

```
$ cd $PIO_HOME/examples/scala-local-helloworld
$ cp ../data/helloworld/data2.csv ../data/helloworld/data.csv
```

```
$ ../../bin/pio train
$ ../../bin/pio deploy
```

```
$ curl -H "Content-Type: application/json" -d '{ "day": "Mon" }' http://localhost:8000/queries.json

{"temperature":76.66666666666667}
```
