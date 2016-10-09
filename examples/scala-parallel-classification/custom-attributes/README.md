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

# Classification Engine Template with Custom Attributes and Random Forest Algorithm

This example engine is based on Classification Tempplate version v0.1.1 and is modified to use Random Forest algorithm and demonstrates how to use custom attributes for classification.

## Classification template

Please refer to http://predictionio.incubator.apache.org/templates/classification/quickstart/

## Development Notes

### Example Use Case

predict the service plan (plan) a user will subscribe to based on his 3 properties: Gender, Age and Education

### Input Data:

Prepared below input data in the format  “Plan, Gender, Age, Education”

0,Male,30,College
0,Female,29,College
1,Male,30,High School
1,Female,28,High School
2,Male,35,No School
2,Male,40,No School
2,Female,25,No School
0,Male,40,College

Input data is available in data/sample_random_forest_data.txt

### Importing  Input data

Updated “import_eventserver.py” withe below changes

1) added “sample_random_forest_data.txt” as file
  ```
  parser.add_argument('--file', default="./data/sample_random_forest_data.txt")
  ```
2) Creating events with features.
  ```
	client.create_event(
      event="$set",
      entity_type="user",
      entity_id=str(count), # use the count num as user ID
      properties= {
        "gender" : data[1],
        "age" : int(data[2]),
        "education" : data[3],
        "plan" : int(data[0])
      }
    )
  ```
### Changes to engine.json

In the engine.json, removed “naive” algorithm and  added “randomforest” algorithm.
```
"algorithms": [
    {
      "name": "randomforest",
      "params": {
        "numClasses": 3,
        "numTrees": 5,
        "featureSubsetStrategy": "auto",
        "impurity": "gini",
        "maxDepth": 4,
        "maxBins": 100
      }
    }
  ]
```

### Changes to DataSource.scala

1) In the  readTraining method, defined below maps
 ```scala
 val gendersMap = Map("Male" -> 0.0, "Female" -> 1.0)
 val educationMap = Map("No School" -> 0.0,"High School" -> 1.0,"College" -> 2.0)
 ```
Then encoded the categorical features values using map.
```scala
 LabeledPoint(properties.get[Double]("plan"),
            Vectors.dense(Array(
              gendersMap(properties.get[String]("gender")),
              properties.get[Double]("age"),
              educationMap(properties.get[String]("education"))
            ))
          )
```

2) Added gendersMap and educationMap to the TrainingData class
```scala
class TrainingData(
  val labeledPoints: RDD[LabeledPoint],
  val gendersMap: Map[String,Double],
  val educationMap: Map[String,Double]
) extends Serializable
```
readTraining returns below:
```scala
	 new TrainingData(labeledPoints,
   	     gendersMap,
        educationMap)

```

### Changes to Engine.scala

In the Engine.scala, replaced “naive” algorithm with “randomforest” algorithm
```scala
 Map("randomforest" -> classOf[RandomForestAlgorithm]),
```
Updated Query.sclaa to include attributes
```scala
class Query(
 val  gender: String,
 val  age: Int,
 val  education: String
) extends Serializable
```
### Changes to Preparator.scala

added attributes to PreparedData
```scala
	class PreparedData(
 		 val labeledPoints: RDD[LabeledPoint],
  val gendersMap: Map[String,Double],
  val educationMap: Map[String,Double]
) extends Serializable
```
```scala
new PreparedData(trainingData.labeledPoints,trainingData.gendersMap,trainingData.educationMap)

```


### Created RandomForestAlgorithm.scala

created  new model class
```scala
class PIORandomForestModel(
  val gendersMap: Map[String, Double],
  val educationMap: Map[String, Double],
  val randomForestModel: RandomForestModel
) extends Serializable
```
train method returns new model class
```scala
 new PIORandomForestModel(
    gendersMap = data.gendersMap,
    educationMap = data.educationMap,
    randomForestModel = m
   )
```

Predict method implementation
```scala
 def predict(
    model: PIORandomForestModel, // CHANGED
    query: Query): PredictedResult = {
    val gendersMap = model.gendersMap
    val educationMap = model.educationMap
    val randomForestModel = model.randomForestModel
    val label = randomForestModel.predict(Vectors.dense(Array(gendersMap(query.gender),query.age.toDouble,educationMap(query.education))))
    new PredictedResult(label)
  }

```


### Sample Request
```
curl -H "Content-Type: application/json" -d '{ "gender":"Male",
"age":30,
"education":"College" }' http://localhost:8000/queries.json  

curl -H "Content-Type: application/json" -d '{ "gender":"Female",
"age":35,
"education":"No School" }' http://localhost:8000/queries.json  

```
