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

#Release Notes and News

**Note:** For upgrade instructions please refer to [this page](http://predictionio.incubator.apache.org/resources/upgrade/).

## Version History

### 0.10.0

Aug ??, 2016

 - Make SSL optional
 - Merge ActionML fork
 - First Apache PredictionIO (incubating) release

### 0.9.7-aml (ActionML fork)

Aug 5, 2016

 - changed version id so artifacts don't conflict with naming in the Salesforce sponsored project.
 - bug fix in memory use during moving window event trim and compaction  EventStore data.
 - update [install.sh](https://github.com/actionml/PredictionIO/blob/master/bin/install.sh) script for single line installs with options that support various combinations required by some templates.
 
### v0.9.6

April 11, 2015 

For a detailed list of commits check [this page](https://github.com/apache/incubator-predictionio/commits/master)

- Upgrade components for install/runtime to Hbase 1, Spark 1.5.2 PIO still runs on older HBase and Spark back to 1.3.1, upgrading install of Elaticsearch to 1.5.2 since pio run well on it but also runs on older versions.
- Support for maintaining a moving window of events by discarding old events from the EventStore
- Support for doing a deploy without creating a Spark Context

### v0.9.6 (ActionML fork)

March 26, 2016

- Upgrade components for install/runtime to Hbase 1.X, Spark 1.5.2 PIO still runs on older HBase and Spark back to 1.3.1, upgrading install of Elasticsearch to 1.5.2 since pio run well on it but also runs on older versions.
- Support for maintaining a moving window of events by discarding old events from the EventStore
- Support for doing a deploy without creating a Spark Context

###v0.9.5 

October 14th, 2015 

[Release Notes](https://github.com/apache/incubator-predictionio/blob/master/RELEASE.md) have been moved to Github and you are reading them. For a detailed list of commits check [this page](https://github.com/apache/incubator-predictionio/commits/v0.9.5)

- Support batches of events sent to the EventServer as json arrays
- Support creating an Elasticsearch StorageClient created for an Elasticsearch cluster from variables in pio-env.sh
- Fixed some errors installing PredictionIO through install.sh when SBT was not correctly downloaded

###v0.9.4

July 16th, 2015

Release Notes

- Support event permissions with different access keys at the Event Server interface
- Support detection of 3rd party Apache Spark distributions
- Support running `pio eval` without `engine.json`
- Fix an issue where `--verbose` is not handled properly by `pio train`

###v0.9.3

May 20th, 2015


Release Notes

- Add support of developing prediction engines in Java
- Add support of PostgreSQL and MySQL
- Spark 1.3.1 compatibility fix
- Creation of specific app access keys
- Prevent a case where `pio build` accidentally removes PredictionIO core library

###v0.9.2

April 14th, 2015

Release Notes

- Channels in the Event Server
- Spark 1.3+ support (upgrade to Spark 1.3+ required)
- [Webhook Connector](http://predictionio.incubator.apache.org/community/contribute-webhook/) support
- Engine and Event Servers now by default bind to 0.0.0.0
- Many documentation improvements

###v0.9.1

March 17th, 2015

Release Notes

- Improved `pio-start-all`
- Fixed a bug where `pio build` failed to set PredictionIO dependency version for engine templates

###v0.9.0

March 4th, 2015

Release Notes

- [E-Commerce Recommendation Template](http://predictionio.incubator.apache.org/gallery/template-gallery#recommender-systems) which includes 1) out-of-stock items support 2) new user recommendation 3) unseen items only
- [Complementary Purchase Template](http://predictionio.incubator.apache.org/gallery/template-gallery#unsupervised-learning) for shopping cart recommendation
- [Lead Scoring Template](http://predictionio.incubator.apache.org/gallery/template-gallery#classification) predicts the probability of an user will convert in the current session
- `pio-start-all`, `pio-stop-all` commands to start and stop all PredictionIO related services

###v0.8.6

Feb 10th, 2015

Release Notes

- New engine template - [Product Ranking](/templates/productranking/quickstart/) for personalized product listing
- [CloudFormation deployment](/system/deploy-cloudformation/) available
