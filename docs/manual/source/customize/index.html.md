---
title: Learning DASE
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

The code of an engine consists of D-A-S-E components:

### [D] Data Source and Data Preparator

Data Source reads data from an input source and transforms it into a desired
format. Data Preparator preprocesses the data and forwards it to the algorithm
for model training.

### [A] Algorithm

The Algorithm component includes the Machine Learning algorithm, and the
settings of its parameters, determines how a predictive model is constructed.

### [S] Serving

The Serving component takes prediction *queries* and returns prediction results.
If the engine has multiple algorithms, Serving will combine the results into
one. Additionally, business-specific logic can be added in Serving to further
customize the final returned results.

### [E] Evaluation Metrics

An Evaluation Metric quantifies prediction accuracy with a numerical score. It
can be used for comparing algorithms or algorithm parameter settings.

> Apache PredictionIO helps you modularize these components so you
can build, for example, several Serving components for an Engine. You will be
able to choose which one to be deployed when you create an Engine.


![Engine Overview](/images/engineinstance-overview.png)

## The Roles of an Engine

The main functions of an engine are:

* Train a model using the training data and be deployed as a web service
* Respond to prediction query in real-time

An engine puts all DASE components into a deployable state by specifying:

* One Data Source

* One Data Preparator

* One or more Algorithm(s)

* One Serving

INFO: If more than one algorithm is specified, each of their model prediction
results will be passed to Serving for ensembling.

Each Engine processes data and constructs predictive models independently.
Therefore, every engine serves its own set of prediction results. For example,
you may deploy two engines for your mobile application: one for recommending
news to users and another one for suggesting new friends to users.

### Training a Model - The DASE View

The following graph shows the workflow of DASE components when `pio train` is run.

![Engine Overview](/images/engine-training.png)


### Respond to Prediction Query - The DASE View

The following graph shows the workflow of DASE components when a REST query is received by a deployed engine.

![Engine Overview](/images/engine-query.png)

Please see [Implement DASE](/customize/dase) for DASE implementation details.

Please refer to following templates and their how-to guides for concrete examples.

## Examples of DASE

- [DASE of Recommendation Template](/templates/recommendation/dase/)
- [DASE of Similar Product Template](/templates/similarproduct/dase/)
- [DASE of Classification Template](/templates/classification/dase/)
- [DASE of Lead Scoring Template](/templates/leadscoring/dase/)
