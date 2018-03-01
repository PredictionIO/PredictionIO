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

# Release Notes and News

**Note:** For upgrade instructions please refer to [this page](http://predictionio.apache.org/resources/upgrade/).

## Version History

### 0.12.0

Sep ?, 2017

#### New Features

- [PIO-61](https://issues.apache.org/jira/browse/PIO-61): S3 support for model data
- [PIO-69](https://issues.apache.org/jira/browse/PIO-69), [PIO-91](https://issues.apache.org/jira/browse/PIO-91): Binary distribution of PredictionIO
- [PIO-105](https://issues.apache.org/jira/browse/PIO-105), [PIO-110](https://issues.apache.org/jira/browse/PIO-110), [PIO-111](https://issues.apache.org/jira/browse/PIO-111): Batch predictions
- [PIO-95](https://issues.apache.org/jira/browse/PIO-95): Raise request timeout for REST API to 35-seconds
- [PIO-114](https://issues.apache.org/jira/browse/PIO-114): Basic HTTP authentication for Elasticsearch 5.x StorageClient
- [PIO-116](https://issues.apache.org/jira/browse/PIO-116): PySpark support

#### Breaking changes

- [PIO-106](https://issues.apache.org/jira/browse/PIO-106): Elasticsearch 5.x StorageClient should reuse RestClient (see the [pull request](https://github.com/apache/predictionio/pull/421))

#### Behavior Changes

- [PIO-59](https://issues.apache.org/jira/browse/PIO-59): `pio app new` uses /dev/urandom/ to generate entropy.
- [PIO-72](https://issues.apache.org/jira/browse/PIO-72): `pio-shell` properly loads storage dependencies.
- [PIO-83](https://issues.apache.org/jira/browse/PIO-83), [PIO-119](https://issues.apache.org/jira/browse/PIO-119): Default environment changed to Spark 2.1.1, Scala 2.11.8,
  and Elasticsearch 5.5.2.
- [PIO-99](https://issues.apache.org/jira/browse/PIO-99): `pio-build` checks for compilation errors before proceeding
  to build engine.
- [PIO-100](https://issues.apache.org/jira/browse/PIO-100): `pio` commands no longer display SLF4J warning messages.

#### Other Changes

- [PIO-56](https://issues.apache.org/jira/browse/PIO-56): Core unit tests no longer require meta data setup.
- [PIO-60](https://issues.apache.org/jira/browse/PIO-60), [PIO-62](https://issues.apache.org/jira/browse/PIO-62): Minor fixes in authorship information and license checking.
- [PIO-63](https://issues.apache.org/jira/browse/PIO-63): Apache incubator logo and disclaimer is displayed on the website.
- [PIO-65](https://issues.apache.org/jira/browse/PIO-65): Integration tests on Travis caches downloaded jars.
- [PIO-66](https://issues.apache.org/jira/browse/PIO-66): More detailed documentation regarding release process and adding
  JIRA tickets.
- [PIO-90](https://issues.apache.org/jira/browse/PIO-90): Improved performance for /batch/events.json API call.
- [PIO-94](https://issues.apache.org/jira/browse/PIO-94): More detailed stack trace for REST API errors.
- [PIO-97](https://issues.apache.org/jira/browse/PIO-97): Update examples in official templates.
- [PIO-102](https://issues.apache.org/jira/browse/PIO-102), [PIO-117](https://issues.apache.org/jira/browse/PIO-117), [PIO-118](https://issues.apache.org/jira/browse/PIO-118), [PIO-120](https://issues.apache.org/jira/browse/PIO-120): Bug fixes, refactoring, and
  improved performance on Elasticsearch behavior.
- [PIO-104](https://issues.apache.org/jira/browse/PIO-104): Bug fix regarding plugins.
- [PIO-107](https://issues.apache.org/jira/browse/PIO-107): Obsolete experimental examples are removed.

#### Credits

The following contributors have spent a great deal of effort to bring to you
this release:

Aayush Kumar, Chan Lee, Donald Szeto, Hugo Duksis, Juha Syrjälä, Lucas Bonatto,
Marius Rabenarivo, Mars Hall, Naoki Takezoe, Nilmax Moura, Shinsuke Sugaya,
Tomasz Stęczniewski, Vaghawan Ojha

### 0.11.0

Apr 25, 2017

#### New Features

- [PIO-30](https://issues.apache.org/jira/browse/PIO-30): Scala 2.11 support
- [PIO-30](https://issues.apache.org/jira/browse/PIO-30): Spark 2 support
- [PIO-49](https://issues.apache.org/jira/browse/PIO-49): Elasticsearch 5 support
- [PIO-30](https://issues.apache.org/jira/browse/PIO-30), [PIO-49](https://issues.apache.org/jira/browse/PIO-49): Flexible build system
- [PIO-47](https://issues.apache.org/jira/browse/PIO-47), [PIO-51](https://issues.apache.org/jira/browse/PIO-51): Removal of engine manifests
- [PIO-49](https://issues.apache.org/jira/browse/PIO-49): Modularized storage backend modules
- [PIO-45](https://issues.apache.org/jira/browse/PIO-45): Self cleaning data source

#### Behavior Changes

- [PIO-25](https://issues.apache.org/jira/browse/PIO-25): `pio-start-all` will no longer start PostgreSQL if it is not being
  used.
- [PIO-47](https://issues.apache.org/jira/browse/PIO-47), [PIO-51](https://issues.apache.org/jira/browse/PIO-51): `pio build` no longer requires access to the metadata
  repository. `pio` commands will now accept an optional `--engine-dir`
  parameter if you want to run `pio build`, `pio train`, or `pio deploy` outside
  of an engine directory. This is an interim solution before an engine registry
  feature becomes available in the future.
- [PIO-49](https://issues.apache.org/jira/browse/PIO-49): PostgreSQL JDBC driver is no longer bundled with the core assembly. If
  you are using PostgreSQL, you must download the JDBC driver and update your
  configuration to point to the correct JDBC driver file.
- [PIO-54](https://issues.apache.org/jira/browse/PIO-54): New generated access keys will no longer start with a `-` character.

#### Other Changes

- [PIO-28](https://issues.apache.org/jira/browse/PIO-28): Code refactoring of the command line interface. It is now possible to
  develop new interfaces that perform the same functionalities provided by the
  CLI.
- [PIO-53](https://issues.apache.org/jira/browse/PIO-53): Integration tests can now be tied to every single Git commit, without
  the need to update the official test Docker image.
- The meta data and model data access object methods are now public and marked
  as Core Developer API.

#### Credits

The following contributors have spent a great deal of effort to bring to you
this release:

Ahmet DAL, Alexander Merritt, Amy Lin, Bansari Shah, Chan Lee, Chris Woodford,
Daniel Gabrieli, Dennis Jung, Donald Szeto, Emily Rose, Hari Charan Ayada,
infoquestsolutions, Jonny Daenen, Kenneth Chan, Laertis Pappas, Marcin
Ziemiński, Naoki Takezoe, Rajdeep Dua, Shinsuke Sugaya, Pat Ferrel, scorpiovn,
Suneel Marthi, Steven Yan, Takahiro Hagino, Takako Shimamoto

### 0.10.0

Oct 7, 2016

 - Make SSL optional
 - Merge ActionML fork
 - First Apache PredictionIO release

### 0.9.7-aml (ActionML fork)

Aug 5, 2016

 - changed version id so artifacts don't conflict with naming in the Salesforce sponsored project.
 - bug fix in memory use during moving window event trim and compaction  EventStore data.
 - update [install.sh](https://github.com/actionml/PredictionIO/blob/master/bin/install.sh) script for single line installs with options that support various combinations required by some templates.

### 0.9.6

April 11, 2015

For a detailed list of commits check [this page](https://github.com/apache/predictionio/commits/v0.9.6)

- Upgrade components for install/runtime to Hbase 1, Spark 1.5.2 PIO still runs on older HBase and Spark back to 1.3.1, upgrading install of Elaticsearch to 1.5.2 since pio run well on it but also runs on older versions.
- Support for maintaining a moving window of events by discarding old events from the EventStore
- Support for doing a deploy without creating a Spark Context

### 0.9.6 (ActionML fork)

March 26, 2016

- Upgrade components for install/runtime to Hbase 1.X, Spark 1.5.2 PIO still runs on older HBase and Spark back to 1.3.1, upgrading install of Elasticsearch to 1.5.2 since pio run well on it but also runs on older versions.
- Support for maintaining a moving window of events by discarding old events from the EventStore
- Support for doing a deploy without creating a Spark Context

### 0.9.5

October 14th, 2015

[Release Notes](https://github.com/apache/predictionio/blob/master/RELEASE.md) have been moved to Github and you are reading them. For a detailed list of commits check [this page](https://github.com/apache/predictionio/commits/v0.9.5)

- Support batches of events sent to the EventServer as json arrays
- Support creating an Elasticsearch StorageClient created for an Elasticsearch cluster from variables in pio-env.sh
- Fixed some errors installing PredictionIO through install.sh when SBT was not correctly downloaded

### 0.9.4

July 16th, 2015

Release Notes

- Support event permissions with different access keys at the Event Server interface
- Support detection of 3rd party Apache Spark distributions
- Support running `pio eval` without `engine.json`
- Fix an issue where `--verbose` is not handled properly by `pio train`

### 0.9.3

May 20th, 2015


Release Notes

- Add support of developing prediction engines in Java
- Add support of PostgreSQL and MySQL
- Spark 1.3.1 compatibility fix
- Creation of specific app access keys
- Prevent a case where `pio build` accidentally removes PredictionIO core library

### 0.9.2

April 14th, 2015

Release Notes

- Channels in the Event Server
- Spark 1.3+ support (upgrade to Spark 1.3+ required)
- [Webhook Connector](http://predictionio.apache.org/community/contribute-webhook/) support
- Engine and Event Servers now by default bind to 0.0.0.0
- Many documentation improvements

### 0.9.1

March 17th, 2015

Release Notes

- Improved `pio-start-all`
- Fixed a bug where `pio build` failed to set PredictionIO dependency version for engine templates

### 0.9.0

March 4th, 2015

Release Notes

- [E-Commerce Recommendation Template](http://predictionio.apache.org/gallery/template-gallery#recommender-systems) which includes 1) out-of-stock items support 2) new user recommendation 3) unseen items only
- [Complementary Purchase Template](http://predictionio.apache.org/gallery/template-gallery#unsupervised-learning) for shopping cart recommendation
- [Lead Scoring Template](http://predictionio.apache.org/gallery/template-gallery#classification) predicts the probability of an user will convert in the current session
- `pio-start-all`, `pio-stop-all` commands to start and stop all PredictionIO related services

### 0.8.6

Feb 10th, 2015

Release Notes

- New engine template - [Product Ranking](/templates/productranking/quickstart/) for personalized product listing
- [CloudFormation deployment](/system/deploy-cloudformation/) available
