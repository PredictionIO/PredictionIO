---
title: Reading Custom Properties (Classification)
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

By default, the classification template reads 4 properties of a user entity: "attr0", "attr1", "attr2" and "plan". You can modify the [default DataSource](dase.html#data) to read your custom properties or different Entity Type.

In this example, we modify DataSource to read properties "featureA", "featureB", "featureC", "featureD" and "label" for entity type "item". You can find the complete modified source code [here](https://github.com/apache/predictionio/tree/develop/examples/scala-parallel-classification/reading-custom-properties).

>> Note: you also need import events with these properties accordingly.

Modify the `readTraining()` and `readEval()` in DataSource.scala:

- modify the `entityType` parameter
- modify the list of properties names in the `required` parameter
- modify how to create the `LabeledPoint` object using the entity properties

```scala
  def readTraining(sc: SparkContext): TrainingData = {
    ...
    val labeledPoints: RDD[LabeledPoint] = PEventStore.aggregateProperties(
      appName = dsp.appName,
      entityType = "item", // MODIFIED
      // only keep entities with these required properties defined
      required = Some(List( // MODIFIED
        "featureA", "featureB", "featureC", "featureD", "label")))(sc)
      // aggregateProperties() returns RDD pair of
      // entity ID and its aggregated properties
      .map { case (entityId, properties) =>
        try {
          // MODIFIED
          LabeledPoint(properties.get[Double]("label"),
            Vectors.dense(Array(
              properties.get[Double]("featureA"),
              properties.get[Double]("featureB"),
              properties.get[Double]("featureC"),
              properties.get[Double]("featureD")
            ))
          )
        } catch {
          case e: Exception => {
            logger.error(s"Failed to get properties ${properties} of" +
              s" ${entityId}. Exception: ${e}.")
            throw e
          }
        }
      }.cache()
    ...
  }
```

Lastly, redefine the Query class parameters to take in four double values: featureA, featureB, featureC, and featureD. Now, to send a query, the field names must be changed accordingly:

```
$ curl -H "Content-Type: application/json" -d '{ "featureA":2, "featureB":0, "featureC":0, "featureD":0 }' http://localhost:8000/queries.json
```

That's it! Now your classification engine is using different properties as training data.
