---
layout: docs
title: Overview
---

# Overview

PredictionIO is comprised with two main components: **Event Server** and
**Engine**. It ingests data from an application and outputs prediction results.

![System Overview Simple]({{site.baseurl}}/images/system-overview-simple.png)


After [installing PredictionIO]({{site.baseurl}}/install), your application can send data to
PredictionIO's *Event Server* through its *Event API*.

## App

You can send data to PredictionIO from any application, be it a website or
mobile app. Each application can be identified by a unique *app_id*. You can use
PredictionIO with multiple apps at the same time.

## Event Server

Event Server is designed to collect data into PredictionIO in an event-based
style. Once the Event Server is launched, your application can send data to it
through its Event API with HTTP requests or with the EventClient of
PredictionIO's SDKs.

Alternatively, you can import data from your own datastore instead of using
Event Server.

## Engine and Engine Instance

The core of PredictionIO. An Engine represents a type of Machine Learning task.
PredictionIO comes with a few built-in *Engines* for different types of Machine
Learning tasks, e.g. Personalized Item Recommendation and Item Ranking.

An Engine has the following components: DataSource, Data Preparator(Preparator),
Algorithm, and Serving. To learn more about Engine and Engine Instance, please
refer to [Engine and Engine Instance: A Closer Look]({{site.baseurl}}/engines/concept).

PredictionIO is extremely flexible. If you want to run multiple types of prediction for one app or want to combine the results from different algorithms, you can run multiple instances of engines. 

![System Overview]({{site.baseurl}}/images/system-overview.png)

You may also [build your own Engines]({{site.baseurl}}/enginebuilders) for specific type of
prediction problems.

You can create one or more *Engine Instance(s)* from an Engine. An Engine
Instance trains a predictive model according to its own parameter settings. After
an Engine Instance is deployed by PredictionIO, your application can send query
to it through its *Engine API* to retrieve prediction results.

Next, you can follow the [Quick Start Guide]({{site.baseurl}}/tutorials/engines/quickstart.html) and set up your first PredictionIO instance. 
