---
title: App Integration Overview
---

PredictionIO is designed as a machine learning server that integrates with your applications on production environments.

A web or mobile app normally:

1. Send event data to PredictionIO's Event Server for model training
2. Send dynamic queries to deployed engine(s) to retrieve predicted results

![PredictionIO Single Engine Overview](/images/overview-singleengine.png)

## Sending Event Data

PredictionIO's Event Server receives event data from your application. The data can be used by engines as training data to build preditive models.

Event Server listens to port 7070 by default. You can change the port with the [--port arg](/cli/#event-server-commands) when you launch the Event Server.

For further information, please read:

* [Event Server Overview](/datacollection/)
* [Collecting Data with REST/SDKs](/datacollection/eventapi)

## Sending Query

After you deploy an engine as a web service,
it will wait for queries from your application and return predicted results in JSON format.  An engine listens to port 8000 by default. If you want to deploy multiple engines, you can specify a different port for each of them.

For further information, please read:

* [Deploying an Engine as a Web Service](/deploy/)
