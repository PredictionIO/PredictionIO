---
title: Engine Server Plugin
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

You can write engine server plugins to handle output data. For example, it's able to transform or log prediction result. There are two types of engine server plugin.

- `Output Blocker`: Before predictions go out, they will be processed through all loaded and active plugins. The order of processing is not defined. They are useful for transforming prediction results (e.g. if you do not have access to engine source code).
- `Output Sniffer`: These should have similar benefits with event server sniffers.

## Create an engine server plugin

At first, create a sbt project with following `build.sbt`:

```scala
name := "pio-plugin-example"
version := "1.0"
scalaVersion := "2.11.11"
libraryDependencies += "org.apache.predictionio" %% "apache-predictionio-core" % "0.12.1"
```

Engine server plug-ins must extend `EngineServerPlugin`. Here is an example of engine server plug-in:

```scala
package com.example

import org.apache.predictionio.data.storage.EngineInstance
import org.apache.predictionio.workflow._
import org.json4s.JValue

class MyEngineServerPlugin extends EngineServerPlugin {
  val pluginName = "my-engineserver-plugin"
  val pluginDescription = "an example of engine server plug-in"
  
  // inputBlocker or inputSniffer
  val pluginType = EngineServerPlugin.outputBlocker	
  
  // Plug-in can handle output data in this method.
  override def process(
      engineInstance: EngineInstance,
      query: JValue,
      prediction: JValue,
      context: EngineServerPluginContext): JValue = {
    println(prediction)
    prediction
  }

  // Plug-in can handle requests to /plugins/<pluginType>/<pluginName>/* 
  // on the engine server in this method.
  override def handleREST(arguments: Seq[String]): String = {
     """{"pluginName": "my-engineserver-plugin"}"""
  }
}
```

Plug-ins are loaded by `ServiceLoader`, so you must create `META-INF/services/org.apache.predictionio.workflow.EngineServerPlugin` with a following content:	

```
com.example.MyEngineServerPlugin
```

Then, run `sbt package` to package plugin as a jar file. In this case, the plugin jar file is generated at `target/scala-2.11/pio-plugin-example_2.11-1.0.jar`, so copy this file to `PIO_HOME/plugins`.

To enable plugins, you have to modify `engine.json` in the root directory of your engine as follows. Defined plugins parameters can be accessed via `EngineServerPluginContext` in plugins.

```json
{
  "id": "default",
  "description": "Default settings",
  "engineFactory": "org.example.recommendation.RecommendationEngine",
  "plugins": {
    "my-engineserver-plugin": {
      "enabled": true
    }
  },
  ...
}
```

When you start (or restart) the engine server, this plugin should be enabled.

## Plugin APIs of engine server

The engine server has some plugins related APIs:

- `/plugins.json`: Show all enabled plugins.
- `/plugins/outputblocker/<pluginName>/*`: Handled by a corresponding output blocker plugin.
- `/plugins/outputsniffer/<pluginName>/*`: Handled by a corresponding output sniffer plugin.

For example, if you send following request to the engine server:
	
```
curl -XGET http://localhost:7070/plugins.json?accessKey=$ACCESS_KEY
```

The engine server should respond following JSON response:
	
```json
{
  "plugins": {
    "outputblockers": {
      "my-engineserver-plugin": {
        "name": "my-engineserver-plugin",
        "description": "an example of engine server plug-in",
        "class": "com.example.MyEngineServerPlugin",
        "params": {
          "enabled": true
        }
      }
    },
    "outputsniffers": {}
  }
}
```