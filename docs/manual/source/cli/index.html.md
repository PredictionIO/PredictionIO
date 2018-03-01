---
title: Command Line
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

##Overview

Interaction with Apache PredictionIO is done through the command
line interface. It follows the format of:

```pio <command> [options] <args>...```

You can run ```pio help``` to see a list of all available commands and ```pio
help <command>``` to see details of the command.

Apache PredictionIO commands can be separated into the following
three categories.

##General Commands
```pio help```          Display usage summary. `pio help <command>` to read about a specific subcommand.

```pio version```       Displays the version of the installed PredictionIO.

```pio status```        Displays install path and running status of PredictionIO system and its dependencies.


##Event Server Commands

```pio eventserver```   Launch the Event Server.

```pio app```           Manage apps that are used by the Event Server.

```pio app data-delete <name>``` deletes all data associated with the app.

```pio app delete <name>``` deletes the app and its data.

  ```--ip <value>``` IP to bind to. Default to localhost.

  ```--port <value>``` Port to bind to. Default to 7070.


```pio accesskey```     Manage app access keys.


##Engine Commands
Engine commands need to be run from the directory that contains the engine
project. ```--debug``` and ```--verbose``` flags will provide debug and
third-party informational messages.

```pio build```         Build the engine at the current directory.

```pio train```         Kick off a training using an engine.

```pio deploy```        Deploy an engine as an engine server.

```pio batchpredict```  Process bulk predictions using an engine.

For ```deploy``` & ```batchpredict```, if ```--engine-instance-id``` is not
specified, it will use the latest trained instance.
