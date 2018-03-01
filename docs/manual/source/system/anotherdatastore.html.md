---
title: Using Another Data Store
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

PredictionIO has a thin storage layer to abstract meta data, event data, and
model data access. The layer defines a set of standard interfaces to support
multiple data store backends. PredictionIO users can configure the backend of
choice through configuration files or environmental variables. Engine developers
need not worry about the actual underlying storage architecture. Advanced
developers can implement their own backend driver as an external library.


## Concepts

In this section, we will visit some storage layer concepts that are common to
users, engine developers, and advanced developers:

- **Repository** is the highest level of data access abstraction and is where all
engines and PredictionIO itself access data with.

- **Source** is the actual data store backend that provide data access. A source is an
implementation of the set of data access interfaces defined by *repositories*.

Each of them will be explained in detail below:

### Repositories

*Repository* is the highest level of data access abstraction and is where all
engines and PredictionIO itself access data with.

The storage layer currently defines three mandatory data repositories: *meta
data*, *event data*, and *model data*. Each repository has its own set of data
access interfaces.

- **Meta data** is used by PredictionIO to store engine training and evaluation
information. Commands like `pio build`, `pio train`, `pio deploy`, and `pio
eval` all access meta data.

- **Event data** is used by the Event Server to collect events, and by engines to
source data.

- **Model data** is used by PredictionIO for automatic persistence of trained
models.

The following configuration variables are used for configure these repositories:

  - *Meta data* is configured by the `PIO_STORAGE_REPOSITORIES_METADATA_XXX` variables.
  - *Event data* is configured by the `PIO_STORAGE_REPOSITORIES_EVENTDATA_XXX` variables.
  - *Model data* is configured by the `PIO_STORAGE_REPOSITORIES_MODELDATA_XXX` variables.

Configuration variables will be explained in more details in later sections below (see Data Store Configuration).

For example, you may see the following configuration variables defined in `conf/pio-env.sh`

```shell
PIO_STORAGE_REPOSITORIES_METADATA_NAME=predictionio_metadata
PIO_STORAGE_REPOSITORIES_METADATA_SOURCE=ELASTICSEARCH

PIO_STORAGE_REPOSITORIES_EVENTDATA_NAME=predictionio_eventdata
PIO_STORAGE_REPOSITORIES_EVENTDATA_SOURCE=HBASE

PIO_STORAGE_REPOSITORIES_MODELDATA_NAME=pio_
PIO_STORAGE_REPOSITORIES_MODELDATA_SOURCE=LOCALFS
```

The configuration variable with the *NAME* suffix controls the namespace used by
the *source*.

The configuration variable with the *SOURCE* suffix points to the actual
**source** that will back this repository. *Source* will be explained below.


### Sources

*Sources* are actual data store backends that provide data access. A source is an
implementation of the set of data access interfaces defined by *repositories*.

PredictionIO comes with the following sources:

- **JDBC** (tested on MySQL and PostgreSQL):
  * Type name is **jdbc**.
  * Can be used for *Meta Data*, *Event Data* and *Model Data* repositories

- **Elasticsearch**:
  * Type name is **elasticsearch**
  * Can be used for *Meta Data* repository

- **Apache HBase**:
  * Type name is **hbase**
  * Can be used for *Event Data* repository

- **Local file system**:
  * Type name is **localfs**
  * Can be used for *Model Data* repository

- **HDFS**:
  * Type name is **hdfs**.
  * Can be used for *Model Data* repository

- **S3**:
  * Type name is **s3**.
  * Can be used for *Model Data* repository

Each repository can be configured to use different sources as shown above.

Each source has its own set of configuration parameters. Configuration variables will be explained in more details in later sections below (see Data Store Configuration).

The following is an example source configuration with name "PGSQL" with type `jdbc`:

```shell
PIO_STORAGE_SOURCES_PGSQL_TYPE=jdbc
PIO_STORAGE_SOURCES_PGSQL_URL=jdbc:postgresql:predictionio
PIO_STORAGE_SOURCES_PGSQL_USERNAME=pio
PIO_STORAGE_SOURCES_PGSQL_PASSWORD=pio
```

The following is an example of using this source "PGSQL" for the *meta data* repository:

```shell
PIO_STORAGE_REPOSITORIES_METADATA_NAME=predictionio_metadata
PIO_STORAGE_REPOSITORIES_METADATA_SOURCE=PGSQL
```

## Data Store Configuration

Data store configuration is done by settings environmental variables. If you set
them inside `conf/pio-env.sh`, they will be automatically available whenever you
perform a `pio` command, e.g. `pio train`.

Notice that all variables are prefixed by `PIO_STORAGE_`.

### Repositories Configuration

Variable Format: `PIO_STORAGE_REPOSITORIES_<REPO>_<KEY>`

Configuration variables of repositories are prefixed by
`PIO_STORAGE_REPOSITORIES_`, followed by the repository name (e.g. `METADATA`),
and then either `NAME` or `SOURCE`.

Consider the following example:

```shell
PIO_STORAGE_REPOSITORIES_METADATA_NAME=predictionio_metadata
PIO_STORAGE_REPOSITORIES_METADATA_SOURCE=PGSQL
```

The above configures PredictionIO to look for a source configured with the name
`PGSQL`, and use `predictionio_metadata` as the namespace within such source. There is no
restriction on namespace usage by the source, so behavior may vary. As an
example, the official JDBC source uses the namespace as database table prefix.


### Sources Configuration

Variable Format: `PIO_STORAGE_SOURCES_<NAME>_<KEY>`

Configuration variables of sources are prefixed by
`PIO_STORAGE_SOURCES_`, followed by the source name of choice (e.g. `PGSQL`,
`MYSQL`, `HBASE`, etc), and a configuration `KEY`.

INFO: The `TYPE` configuration key is mandatory. It is used by PredictionIO to
determine the actual driver type to load.

Depending on what the source `TYPE` is, different configuration keys are
required.


#### JDBC Configuration

Variable Format: `PIO_STORAGE_SOURCES_[NAME]_TYPE=jdbc`

Supported Repositories: **meta**, **event**, **model**

Tested on: MySQL 5.1+, PostgreSQL 9.1+

When `TYPE` is set to `jdbc`, the following configuration keys are supported.

-   URL (mandatory)

    The value must be a valid JDBC URL that points to a database, e.g.
    `PIO_STORAGE_SOURCES_PGSQL_URL=jdbc:postgresql:predictionio`

-   USERNAME (mandatory)

    The value must be a valid, non-empty username for the JDBC connection, e.g.
    `PIO_STORAGE_SOURCES_PGSQL_USERNAME=pio_user`

-   PASSWORD (mandatory)

    The value must be a valid, non-empty password for the JDBC connection, e.g.
    `PIO_STORAGE_SOURCES_PGSQL_PASSWORD=pio_user_password`

-   PARTITIONS (optional, default to 4)

    This value is used by Apache Spark to determine the number of partitions to
    use when it reads from the JDBC connection, e.g.
    `PIO_STORAGE_SOURCES_PGSQL_PARTITIONS=4`

-   CONNECTIONS (optional, default to 8)

    This value is used by scalikejdbc library to determine the max size of connection pool, e.g.
    `PIO_STORAGE_SOURCES_PGSQL_CONNECTIONS=8`

-   INDEX (optional since v0.9.6, default to disabled)

    This value is used by creating indexes on entityId and entityType columns to
    improve performance when findByEntity function is called. Note that these columns
    of entityId and entityType will be created as varchar(255), e.g.
    `PIO_STORAGE_SOURCES_PGSQL_INDEX=enabled`


#### Apache HBase Configuration

Variable Format: `PIO_STORAGE_SOURCES_[NAME]_TYPE=hbase`

Supported Repositories: **event**

Tested on: Apache HBase 0.98.5+, 1.0.0+

When `TYPE` is set to `hbase`, no other configuration keys are required. Other
client side HBase configuration must be done through `hbase-site.xml` pointed
by the `HBASE_CONF_DIR` configuration variable.


#### Elasticsearch Configuration

Variable Format: `PIO_STORAGE_SOURCES_[NAME]_TYPE=elasticsearch`

Supported Repositories: **meta**

When `TYPE` is set to `elasticsearch`, the following configuration keys are
supported.

-   HOSTS (mandatory)

    Comma-separated list of hostnames, e.g.
    `PIO_STORAGE_SOURCES_ES_HOSTS=es1,es2,es3`

-   PORTS (mandatory)

    Comma-separated list of ports that corresponds to `HOSTS`, e.g.
    `PIO_STORAGE_SOURCES_ES_PORTS=9200,9200,9222`

-   CLUSTERNAME (optional, default to `elasticsearch`)

    Elasticsearch cluster name, e.g.
    `PIO_STORAGE_SOURCES_ES_CLUSTERNAME=myescluster`

INFO: Other advanced Elasticsearch parameters can be set by pointing
`ES_CONF_DIR` configuration variable to the location of `elasticsearch.yml`.


#### Local File System Configuration

Variable Format: `PIO_STORAGE_SOURCES_[NAME]_TYPE=localfs`

Supported Repositories: **model**

When `TYPE` is set to `localfs`, the following configuration keys are
supported.

-   PATH (mandatory)

    File system path at where models are stored, e.g.
    `PIO_STORAGE_SOURCES_FS_PATH=/mymodels`


#### HDFS Configuration

Variable Format: `PIO_STORAGE_SOURCES_[NAME]_TYPE=hdfs`

Supported Repositories: **model**

When `TYPE` is set to `hdfs`, the following configuration keys are
supported.

-   PATH (mandatory)

    HDFS path at where models are stored, e.g.
    `PIO_STORAGE_SOURCES_HDFS_PATH=/mymodels`


#### S3 Configuration

Variable Format: `PIO_STORAGE_SOURCES_[NAME]_TYPE=s3`

Supported Repositories: **model**

To provide authentication information, you can set the `AWS_ACCESS_KEY_ID`
and `AWS_SECRET_ACCESS_KEY` environment variables or use one of the other
methods in the [AWS Setup Docs](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html#config-settings-and-precedence)

When `TYPE` is set to `s3`, the following configuration keys are
supported.

-   REGION (mandatory)

    AWS Region to use, e.g.
    `PIO_STORAGE_SOURCES_S3_REGION=us-east-1`

-   BUCKET_NAME (mandatory)

    S3 Bucket where models are stored, e.g.
    `PIO_STORAGE_SOURCES_S3_BUCKET_NAME=pio_bucket`

-   BASE_PATH (optional)

    S3 base path where models are stored, e.g.
    `PIO_STORAGE_SOURCES_S3_BASE_PATH=pio_model`

-   DISABLE_CHUNKED_ENCODING (optional)

    Disable the use of Chunked Encoding when transferring files to/from S3, e.g.
    `PIO_STORAGE_SOURCES_S3_DISABLE_CHUNKED_ENCODING=true`

-   ENDPOINT (optional)

    S3 Endpoint to use, e.g.
    `PIO_STORAGE_SOURCES_S3_ENDPOINT=http://localstack:4572`


## Adding Support of Other Backends

It is quite straightforward to implement support of other backends. A good
starting point is to reference the JDBC implementation inside the
[org.apache.predictionio.data.storage.jdbc
package](https://github.com/apache/predictionio/tree/develop/data/src/main/scala/org/apache/predictionio/data/storage/jdbc).

Contributions of different backends implementation is highly encouraged. To
start contributing, please refer to [this guide](/community/contribute-code/).


### Deploying Your Custom Backend Support as a Plugin

It is possible to deploy your custom backend implementation as a standalone JAR
apart from the main PredictionIO binary distribution. The following is an
outline of how this can be achieved.

1.  Create an SBT project with a library dependency on PredictionIO's data
    access base traits (inside the `data` artifact).

2.  Implement traits that you intend to support, and package everything into a
    big fat JAR (e.g. sbt-assembly).

3.  Create a directory named `plugins` inside PredictionIO binary installation.

4.  Copy the JAR from step 2 to `plugins`.

5.  In storage configuration, specify `TYPE` as your complete package name. As
    an example, if you have implemented all your traits under the package name
    `org.mystorage.jdbc`, use something like

    ```shell
    PIO_STORAGE_SOURCES_MYJDBC_TYPE=org.mystorage.jdbc
    ...
    PIO_STORAGE_REPOSITORIES_METADATA_SOURCE=MYJDBC
    ```

    to instruct PredictionIO to pick up `StorageClient` from the appropriate
    package.

6.  Now you should be able to use your custom source and assign it to different
    repositories as you wish.
