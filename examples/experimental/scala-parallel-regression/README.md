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

# Parallel Regression Engine

## Configuration

This sample regression engine reads data from file system.

Edit the file path in `engine.json`, change `filepath` of `datasource` to an absolute path that points to
[lr_data.py](../data/lr_data.txt)

```
$ cat engine.json
...
"datasource": {
  "filepath": <absolute_path_to_lr_data.txt>,
  "k": 3,
  "seed": 9527
}
...

```

## Register engine, train, and deploy.

```
$ pio build
$ pio train
$ pio deploy --port 9998
```

## Query the Engine Instance

```
$ curl -X POST http://localhost:9998/queries.json -d \
  '[1.80,0.87,2.41,0.35,-0.21,1.35,0.51,1.55,-0.20,1.32]'

0.8912731719174509
```

0.89... is the prediction result.
