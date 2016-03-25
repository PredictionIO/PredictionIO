---
title: Evaluation Dashboard
---

WARNING: This is an experimental development tool, which exposes environment variables and other sensitive information about the PredictionIO application (e.g. storage configs, credentials etc.). It is not recommended to be run in production.

PredictionIO provides a web dashboard which allows you to see previous
evaluation and a drill down page about each evaluation. It is particularly
useful when we ran multiple [hyperparameter tunings](/evaluation/paramtuning/)
as we may easily lose track of all the engine variants evaluated.

We can start the dashboard with the following command:

```
$ pio dashboard
```

The dashboard lists out all completed evaluations in a reversed chronological
order. A high level description of each evaluation can be seen directly from the
dashboard. We can also click on the *HTML* button to see the evaluation drill
down page.

*Note:* The dashboard server has SSL enabled and is authenticated by a key passed as a query string param `accessKey`. The configuration is in `conf/server.conf`