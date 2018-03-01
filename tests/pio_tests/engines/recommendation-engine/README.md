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

# Recommendation Template

## Documentation

Please refer to http://predictionio.apache.org/templates/recommendation/quickstart/

## Versions

### develop

### v0.3.2

- Fix incorrect top items in batchPredict() (issue #5)

### v0.3.1

- Add Evaluation module and modify DataSource for it

### v0.3.0

- update for PredictionIO 0.9.2, including:

  - use new PEventStore API
  - use appName in DataSource parameter

### v0.2.0

- update build.sbt and template.json for PredictionIO 0.9.2

### v0.1.2

- update for PredictionIO 0.9.0

### v0.1.1

- Persist RDD to memory (.cache()) in DataSource for better performance and quick fix for new user/item ID BiMap error issue.

### v0.1.0

- initial version
- known issue:
  * If importing new events of new users/itesm during training, the new user/item id can't be found in the BiMap.
