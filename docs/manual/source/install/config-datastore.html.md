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
title: Changing Data Store
---

Changing Storage Setup
===================

PredictionIO relies on a data store to store its metadata. At the moment, PredictionIO's storage layer supports [Elasticsearch](http://www.elasticsearch.org/). Make sure you have it running and functioning properly on your computer.

1. If you are using Elasticsearch at the localhost and its default settings, you may stop here.

2. Otherwise, change the following in `conf/pio-env.sh` to fit your setup.

   ```
   PIO_STORAGE_SOURCES_ELASTICSEARCH_TYPE=elasticsearch
   PIO_STORAGE_SOURCES_ELASTICSEARCH_HOSTS=localhost
   PIO_STORAGE_SOURCES_ELASTICSEARCH_PORTS=9300
   ```
<!--
   If you use MongoDB, add and modify the following to fit your setup.

   ```
   PIO_STORAGE_SOURCES_MONGODB_TYPE=mongodb
   PIO_STORAGE_SOURCES_MONGODB_HOSTS=localhost
   PIO_STORAGE_SOURCES_MONGODB_PORTS=27017
   ```

3. The following points the storage repositories to their respective backend
   data sources. By default, they point to Elasticsearch.

   ```
   PIO_STORAGE_REPOSITORIES_METADATA_SOURCE=ELASTICSEARCH
   ```

   If you use MongoDB, change them to something like this.

   ```
   PIO_STORAGE_REPOSITORIES_METADATA_SOURCE=MONGODB
   ```
-->

Save ``conf/pio-env.sh`` and you are done!
