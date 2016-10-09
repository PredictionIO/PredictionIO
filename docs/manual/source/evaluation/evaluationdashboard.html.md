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

---
title: Evaluation Dashboard
---

WARNING: This is an experimental development tool, which exposes environment variables and other sensitive information about the PredictionIO application (e.g. storage configs, credentials etc.). It is not recommended to be run in production.

PredictionIO provides a web dashboard which allows you to see previous
evaluation and a drill down page about each evaluation. It is particularly
useful when we ran multiple [hyperparameter tunings](/evaluation/paramtuning/)
as we may easily lose track of all the engine variants evaluated.

We can start the dashboard with the following command:

```
$ pio dashboard
```

The dashboard lists out all completed evaluations in a reversed chronological
order. A high level description of each evaluation can be seen directly from the
dashboard. We can also click on the *HTML* button to see the evaluation drill
down page.

*Note:* The dashboard server has SSL enabled and is authenticated by a key passed as a query string param `accessKey`. The configuration is in `conf/server.conf`