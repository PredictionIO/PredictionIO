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
Process predictions for many queries using efficient parallelization
through Spark. Useful for mass auditing of predictions and for
generating predictions to push into other systems.

Batch predict reads and writes multi-object JSON files similar to the
[batch import](/datacollection/batchimport/) format. JSON objects are separated
by newlines and cannot themselves contain unencoded newlines.

##Compatibility
`pio batchpredict` loads the engine and processes queries exactly like
`pio deploy`. There is only one additional requirement for engines
to utilize batch predict:

WARNING: All algorithm classes used in the engine must be
[serializable](https://www.scala-lang.org/api/2.11.8/index.html#scala.Serializable).
**This is already true for PredictionIO's base algorithm classes**, but may be broken
by including non-serializable fields in their constructor. Using the
[`@transient` annotation](http://fdahms.com/2015/10/14/scala-and-the-transient-lazy-val-pattern/)
may help in these cases.

This requirement is due to processing the input queries as a
[Spark RDD](https://spark.apache.org/docs/latest/rdd-programming-guide.html#resilient-distributed-datasets-rdds)
which enables high-performance parallelization, even on a single machine.

##Usage

### `pio batchpredict`

Command to process bulk predictions. Takes the same options as `pio deploy` plus:

### `--input <value>`

Path to file containing queries; a multi-object JSON file with one
query object per line. Accepts any valid Hadoop file URL.

Default: `batchpredict-input.json`

### `--output <value>`

Path to file to receive results; a multi-object JSON file with one
object per line, the prediction + original query. Accepts any
valid Hadoop file URL. Actual output will be written as Hadoop
partition files in a directory with the output name.

Default: `batchpredict-output.json`

### `--query-partitions <value>`

Configure the concurrency of predictions by setting the number of partitions
used internally for the RDD of queries. This will directly effect the
number of resulting `part-*` output files. While setting to `1` may seem
appealing to get a single output file, this will remove parallelization
for the batch process, reducing performance and possibly exhausting memory.

Default: number created by Spark context's `textFile` (probably the number
of cores available on the local machine)

### `--engine-instance-id <value>`

Identifier for the trained instance to use for batch predict.

Default: the latest trained instance.

##Example

###Input

A multi-object JSON file of queries as they would be sent to the engine's
HTTP Queries API.

NOTE: Read via
[SparkContext's `textFile`](https://spark.apache.org/docs/latest/rdd-programming-guide.html#external-datasets)
and so may be a single file or any supported Hadoop format.

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

NOTE: Results are written via Spark RDD's `saveAsTextFile` so each partition
will be written to its own `part-*` file.
See [post-processing results](#post-processing-results).

File 1: `batchpredict-output.json/part-00000`

```json
{"query":{"user":"1"},"prediction":{"itemScores":[{"item":"1","score":33},{"item":"2","score":32}]}}
{"query":{"user":"3"},"prediction":{"itemScores":[{"item":"2","score":16},{"item":"3","score":12}]}}
{"query":{"user":"4"},"prediction":{"itemScores":[{"item":"3","score":19},{"item":"1","score":18}]}}
```

File 2: `batchpredict-output.json/part-00001`

```json
{"query":{"user":"2"},"prediction":{"itemScores":[{"item":"5","score":55},{"item":"3","score":28}]}}
{"query":{"user":"5"},"prediction":{"itemScores":[{"item":"1","score":24},{"item":"4","score":14}]}}
```

###Post-processing Results

After the process exits successfully, the parts may be concatenated into a
single output file using a command like:

```bash
cat batchpredict-output.json/part-* > batchpredict-output-all.json
```
