---
title: Event Server Overview
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

Apache PredictionIO offers an Event Server that collects data in an
event-based style via a RESTful API. By default, Event Server uses Apache HBase
as data store.

![EventServer Highlight](/images/eventserver-overview.png)


## What data should I collect?

The Event Server can collect and store arbitrary events. At the beginning of
your project, it is recommended to collect as much data as you can. Later on,
you can exclude data that are not relevant to your predictive model in Data
Preparator.

### Recommendation Engine

With Collaborative Filtering based Recommendation Engine, a common pattern is

```
user -- action -- item
```

where users and items have properties associated with them.

For example, for personalized book recommendation, some events to collect would
be

- User 1 purchased product X
- User 2 viewed product Y
- User 1 added product Z in the cart

User properties can be gender, age, location, etc. Item properties can be genre,
author, and other attributes that may be related to the the user's preference.

Data collection varies quite a bit based on your application and your prediction
goal. We are happy to [assist you with your
questions](mailto:support@prediction.io).
