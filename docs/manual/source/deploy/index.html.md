---
title: Deploying an Engine
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

An engine must be **built** (i.e. `pio build`) and **trained** (i.e. `pio
train`)  before it can be deployed as a web service.

WARNING: The engine server is not protected by authentication, and the
instructions below assume deployment in a trusted environment.

## Deploying an Engine the First Time

After you have [downloaded an Engine Template](/start/download/),  you can deploy it with these steps:

1. Run `pio app new **your-app-name-here**` and specify the `appName` used in the template's *engine.json* file (you can set it there to your preference).
2. Run `pio build` to update the engine
3. Run `pio train` to train a predictive model with training data
4. Run `pio deploy` to deploy the engine as a service

INFO: See [here](/cli/#engine-commands) to know details of command-line interface for the engine server.

A deployed engine listens to port 8000 by default. Your application can [send query to retrieve prediction](/appintegration/) in real-time through the REST interface.

**Note**: a new engine deployed as above will have no data to start with. Your engine may  come with a `data/` directory with some sample data that you can import, not all have this. Check the quickstart instructions for your template.

## Update Model with New Data

You probably want to update the trained predictive model with newly collected data regularly.
To do so, run the `pio train` and `pio deploy` commands again:

```
$ pio train
$ pio deploy
```

For example, if you want to re-train the model every day, you may add this to your *crontab*:

```
0 0 * * *   $PIO_HOME/bin/pio train; $PIO_HOME/bin/pio deploy
```
where *$PIO_HOME* is the installation path of PredictionIO. See [Retrain and Deploy Script](#retrain-and-deploy-script) below for a script ready for customization.


## Specify a Different Engine Port

By default, `pio deploy` deploys an engine on **port 8000**.

You can specify another port with an *--port* argument. For example, to deploy on port 8123

```
pio deploy --port 8123
```

You can also specify the binding IP with *--ip*, which is set to *localhost* if not specified. For example:

```
pio deploy --port 8123 --ip 1.2.3.4
```

## Retrain and Deploy Script

A retrain and deploy script is available [in the *examples/redeploy-script*
directory](https://github.com/apache/predictionio/tree/develop/examples/redeploy-script).

To use the script, copy *local.sh.template* as *local.sh*, *redeploy.sh* as (say) *MyEngine_Redeploy_(production).sh* (Name of the script will appear as title of email) and put both files under the *scripts/* directory of your engine.
Then, modify the settings inside both file, filling in details like `PIO_HOME`, `LOG_DIR`, `TARGET_EMAIL`, `ENGINE_JSON` and others.
You need to do `pio build` once before using this script. This script only trains and deploys.
If `pio train` or `pio deploy` fails for some reason, the running engine stays put in most cases.
If engine is retrained and deployed successfully, the email sent will have *Normal* in the title so you can set filtering rules.

`mailutils` is used in this script. For Ubuntu, you can do `sudo update-alternatives --config mailx` and see if `/usr/bin/mail.mailutils` is selected.
If you are using a server that blocks email, you will need to use services like SendGrid.

This script does not guarantee no down time since at some point during `pio deploy` the original engine is shut down.
The down time is usually not more than a few seconds though it can be more.

The last thing to do is to add this to your *crontab*:

```
0 0 * * *   /path/to/script >/dev/null 2>/dev/null # mute both stdout and stderr to suppress email sent from cron
```
