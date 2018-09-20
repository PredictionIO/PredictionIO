---
title: Tuning and Evaluation
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

PredictionIO's evaluation module allows you to streamline the process of
testing lots of knobs in engine parameters and deploy the best one out
of it using statistically sound cross-validation methods.

There are two key components:

### Engine

It is our evaluation target. During evaluation, in addition to
the *train* and *deploy* mode we describe in earlier sections,
the engine also generates a list of testing data points. These data
points are a sequence of *Query* and *Actual Result* tuples. *Queries* are
sent to the engine and the engine responds with a *Predicted Result*,
in the same way as how the engine serves a query.

### Evaluator

The evaluator joins the sequence of *Query*, *Predicted Result*, and *Actual Result*
together and evaluates the quality of the engine.
PredictionIO enables you to implement any metric with just a few lines of code.

![PredictionIO Evaluation Overview](/images/engine-evaluation.png)

We will discuss various aspects of evaluation with PredictionIO.

- [Hyperparameter Tuning](/evaluation/paramtuning/) - it is an end-to-end example
  of using PredictionIO evaluation module to select and deploy the best engine
  parameter.
- [Evaluation Dashboard](/evaluation/evaluationdashboard/) - it is the dashboard
  where you can see a detailed breakdown of all previous evaluations.
- [Choosing Evaluation Metrics](/evaluation/metricchoose/) - we cover some basic
  machine learning metrics
- [Building Evaluation Metrics](/evaluation/metricbuild/) - we illustrate how to
  implement a custom metric with as few as one line of code (plus some
  boilerplates).
