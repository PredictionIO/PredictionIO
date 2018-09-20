---
title: Glossary
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

**Data Preparator**
- Part of Engine. It reads data from source and transforms it to the desired
format.

**Data Source**
- Part of Engine. It preprocesses the data and forward it to the algorithm for
model training.

**Engine**
- An Engine represents a type of prediction, e.g. product recommendation. It is
comprised of four components: [D] Data Source and Data Preparator, [A]
Algorithm, [S] Serving, [E] Evaluation Metrics.

**EngineClient**
- Part of PredictionSDK. It sends queries to a deployed engine instance through
the Engine API and retrieves prediction results.

**Event API**
- Please see Event Server.

**Event Server**
- Event Server is designed to collect data into PredictionIO in an event-based
style. Once the Event Server is launched, your application can send data to it
through its Event API with HTTP requests or with the EventClient of
PredictionIO's SDKs.

**EventClient**
- Please see Event Server.

**Live Evaluation**
- Evaluation of prediction results in a production environment. Prediction
results are shown to real users. Users do not rate the results explicitly but
the system observes user behaviors such as click through rate.

**Offline Evaluation**
- The prediction results are compared with pre-compiled offline datasets.
Typically, offline evaluations are meant to identify the most promising
approaches.

**Test Data**
- Also commonly referred as Test Set. A set of data used to assess the strength
and utility of a predictive relationship.

**Training Data**
- Also commonly referred as Training Set. A set of data used to discover
potentially predictive relationships. In PredictionIO Engine, training data is
processed through the Data layer and passed onto algorithm.
