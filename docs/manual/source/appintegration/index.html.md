---
title: App Integration Overview
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

Apache PredictionIO is designed as a machine learning server that
integrates with your applications on production environments.

A web or mobile app normally:

1.  Send event data to Apache PredictionIO's Event Server for model
    training
2.  Send dynamic queries to deployed engine(s) to retrieve predicted results

![Apache PredictionIO Single Engine
Overview](/images/overview-singleengine.png)

## Sending Event Data

Apache PredictionIO's Event Server receives event data from your
application. The data can be used by engines as training data to build predictive
models.

Event Server listens to port 7070 by default. You can change the port with the
[--port arg](/cli/#event-server-commands) when you launch the Event Server.

For further information, please read:

* [Event Server Overview](/datacollection/)
* [Collecting Data with REST/SDKs](/datacollection/eventapi)

## Sending Query

After you deploy an engine as a web service, it will wait for queries from your
application and return predicted results in JSON format.  An engine listens to
port 8000 by default. If you want to deploy multiple engines, you can specific a
different port for each of them.

For further information, please read:

* [Deploying an Engine as a Web Service](/deploy/)
