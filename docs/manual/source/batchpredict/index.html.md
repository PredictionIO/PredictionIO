---
title: Batch Predictions
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

##Overview
Process predictions for many queries using efficient parallelization. Useful 
for mass auditing of predictions and for generating predictions to push into
other systems.

Batch predict reads and writes multi-object JSON files similar to the
[batch import](/datacollection/batchimport/) format. JSON objects are separated
by newlines and cannot themselves contain unencoded newlines.

##Compatibility
`pio batchpredict` loads the engine and processes queries exactly like
`pio deploy`.

WARNING: This feature has changed since its initial release in PredictionIO
0.12.0-incubating. Queries are no longer parallelized with Spark, instead using
Scala's built-in parallel collections. As a result, the output is now a simple
JSON file, not a multi-part Hadoop sequence file.

##Usage

### `pio batchpredict`

Command to process bulk predictions. Takes the same options as `pio deploy` plus:

### `--input <value>`

Path to file containing queries; a multi-object JSON file with one
query object per line.

Default: `batchpredict-input.json`

### `--output <value>`

Path to file to receive results; a multi-object JSON file with one
object per line, the prediction + original query.

Default: `batchpredict-output.json`

### `--engine-instance-id <value>`

Identifier for the trained instance to use for batch predict.

Default: the latest trained instance.

##Example

###Input

A multi-object JSON file of queries as they would be sent to the engine's
HTTP Queries API.

File: `batchpredict-input.json`

```json
{"user":"1"}
{"user":"2"}
{"user":"3"}
{"user":"4"}
{"user":"5"}
```

###Execute

```bash
pio batchpredict \
  --input batchpredict-input.json \
  --output batchpredict-output.json
```

This command will run to completion, aborting if any errors are encountered.

###Output

A multi-object JSON file of predictions + original queries. The predictions
are JSON objects as they would be returned from the engine's HTTP Queries API.

File: `batchpredict-output.json`

```json
{"query":{"user":"1"},"prediction":{"itemScores":[{"item":"1","score":33},{"item":"2","score":32}]}}
{"query":{"user":"3"},"prediction":{"itemScores":[{"item":"2","score":16},{"item":"3","score":12}]}}
{"query":{"user":"4"},"prediction":{"itemScores":[{"item":"3","score":19},{"item":"1","score":18}]}}
{"query":{"user":"2"},"prediction":{"itemScores":[{"item":"5","score":55},{"item":"3","score":28}]}}
{"query":{"user":"5"},"prediction":{"itemScores":[{"item":"1","score":24},{"item":"4","score":14}]}}
```
