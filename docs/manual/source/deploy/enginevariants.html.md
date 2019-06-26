---
title: Deploying Multiple Engine Variants
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
In order to deploy multiple variants, then one must specify both the json file containing different variant,
the port that will be serving that, and the instance-id of the engine . The latter information is delivered at the end of the training, and is also stored in the `````id````` column of the `````pio_meta_engineinstances````` table.

`````
pio-docker deploy --engine-instance-id cc8d8de1-16c3-438d-a225-aa0a0ceea29f --variant=engine_01.json --port 8001
pio-docker deploy --engine-instance-id db8048d4-f8a7-4cb8-a64c-f0cab042d511 --variant=engine_02.json --port 8002
pio-docker deploy --engine-instance-id 939e6029-6744-4e66-a0a7-470fb7d8772e --variant=engine_03.json --port 8003
pio-docker deploy --engine-instance-id cc8d8de1-16c3-438d-a225-aa0a0ceea29f --variant=engine_04.json --port 8004
`````

