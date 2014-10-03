---
layout: docs
title: Changing Data Store
---

Changing Storage Setup
===================

PredictionIO relies on a data store to store its metadata. At the moment, PredictionIO's storage layer supports both [Elasticsearch](http://www.elasticsearch.org/) and [MongoDB](http://www.mongodb.org/). Make sure you have one of these running and functioning properly on your computer.

1. If you are using Elasticsearch at the localhost and its default settings, you may stop here.

2. Otherwise, change the following in `conf/pio-env.sh` to fit your setup.
   
   ```
   PIO_STORAGE_SOURCES_ELASTICSEARCH_TYPE=elasticsearch
   PIO_STORAGE_SOURCES_ELASTICSEARCH_HOSTS=localhost
   PIO_STORAGE_SOURCES_ELASTICSEARCH_PORTS=9300
   ```
   
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
   PIO_STORAGE_REPOSITORIES_APPDATA_SOURCE=ELASTICSEARCH
   ```
   
   If you use MongoDB, change them to something like this.
   
   ```
   PIO_STORAGE_REPOSITORIES_METADATA_SOURCE=MONGODB
   PIO_STORAGE_REPOSITORIES_APPDATA_SOURCE=MONGODB
   ```

Save ``conf/pio-env.sh`` and you are done!