---
title: Event Server Plugin
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

You can write event server plugins to handle input data. For example, it's able to block invalid data, log, get statics or forward to other processing systems. There are two types of event server plugin.

- `Input Blocker`: When these plugins are present, events coming into event server will be passed through all loaded and active plugins before reaching the actual event store. The order of processing is not defined, so events can go through these plugins in arbitrary order. One use case is for validating input data and throw exceptions to prevent bad data from going in. These plugins cannot transform the event.
- `Input Sniffer`: When these are present, events will be broadcasted to these plugins in parallel. They do not block the event from reaching event store. They are useful for logging, statistics, and forwarding to other processing systems.

## Create an event server plugin

At first, create a sbt project with following `build.sbt`:

```scala
name := "pio-plugin-example"
version := "1.0"
scalaVersion := "2.11.11"
libraryDependencies += "org.apache.predictionio" %% "apache-predictionio-core" % "0.12.1"
```

Event server plug-ins must extend `EventServerPlugin`. Here is an example of event server plug-in:

```scala
package com.example

import org.apache.predictionio.data.api._

class MyEventServerPlugin extends EventServerPlugin {
  val pluginName = "my-eventserver-plugin"
  val pluginDescription = "an example of event server plug-in"
  
  // inputBlocker or inputSniffer
  val pluginType = EventServerPlugin.inputBlocker	
  
  // Plug-in can handle input data in this method.
  // If plug-in found invalid data, it's possible to block them 
  // by throwing an exception in this method.
  override def process(
      eventInfo: EventInfo, 
      context: EventServerPluginContext): Unit = {
    println(eventInfo)
  }

  // Plug-in can handle requests to /plugins/<pluginType>/<pluginName>/* 
  // on the event server in this method.
  override def handleREST(
      appId: Int, 
      channelId: Option[Int], 
      arguments: Seq[String]): String = {
    """{"pluginName": "my-eventserver-plugin"}"""
  }
}
```

Plug-ins are loaded by `ServiceLoader`, so you must create `META-INF/services/org.apache.predictionio.data.api.EventServerPlugin` with a following content:	

```
com.example.MyEventServerPlugin
```

Finally, run `sbt package` to package plugin as a jar file. In this case, the plugin jar file is generated at `target/scala-2.11/pio-plugin-example_2.11-1.0.jar`, so copy this file to `PIO_HOME/plugins`.

When you start (or restart) the event server, this plugin should be enabled.

## Plugin APIs of event server

The event server has some plugins related APIs:

- `/plugins.json`: Show all enabled plugins.
- `/plugins/inputblocker/<pluginName>/*`: Handled by a corresponding input blocker plugin.
- `/plugins/inputsniffer/<pluginName>/*`: Handled by a corresponding input sniffer plugin.

For example, if you send following request to the event server:
	
```
curl -XGET http://localhost:7070/plugins.json?accessKey=$ACCESS_KEY
```

The event server should respond following JSON response:
	
```json
{
  "plugins": {
    "inputblockers": {
      "my-eventserver-plugin": {
        "name": "my-eventserver-plugin",
        "description": "an example of event server plug-in",
        "class": "com.example.MyEventServerPlugin"
      }
    },
    "inputsniffers": {}
  }
}
```