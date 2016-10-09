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
title: Customizing an Engine
---

When you download an engine template, it comes with the source code. All engine templates follow the same DASE architecture and they are designed to be customizable.

You may want to customize an engine for many reasons, for example: 

* Use another algorithm, or multiple of them
* Read data from a different, or existing, data store
* Read different types of training data
* Transform data with another approach
* Add new evaluation measures
* Add custom business logics


To learn more about DASE, please read "[Learning DASE](/customize/)".

After you have finished modifying the code, you can re-build and deploy the engine again with:

```
$ pio build; pio train; pio deploy
```