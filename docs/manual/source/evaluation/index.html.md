---
title: ML Tuning and Evaluation
---

PredictionIO's evaluation module allows you to streamline the process of
testing lots of knobs in engine parameters and deploy the best one out
of it using statisically sound cross-validation methods.

There are two key components:

### Engine

It is our evaluation target. During evaluation, in addition to 
the *train* and *deploy* mode we describe in earlier sections,
the engine also generates a list of testing data points. These data 
points are a sequence of *Query* and *Actual Result* tuples. *Queries* are
sent to the engine and the engine responds with a *Predicted Result*, 
in the same way as how the engine serves a query.

### Evaluator

The evaluator joins the sequence of *Query*, *Predicted Result*, and *Actual Result* 
together and evaluates the quality of the engine.
PredictionIO enables you to implement any metric with just a few lines of code.

![PredictionIO Evaluation Overview](/images/engine-evaluation.png)

We will discuss various aspects of evaluation with PredictionIO.

- [Hyperparameter Tuning](/evaluation/paramtuning/) - it is an end-to-end example
  of using PredictionIO evaluation module to select and deploy the best engine
  parameter.
- [Evaluation Dashboard](/evaluation/evaluationdashboard/) - it is the dashboard
  where you can see a detailed breakdown of all previous evaluations.
- [Choosing Evaluation Metrics](/evaluation/metricchoose/) - we cover some basic
  machine learning metrics
- [Bulding Evaluation Metrics](/evaluation/metricbuild/) - we illustrate how to
  implement a custom metric with as few as one line of code (plus some
  boilerplates).


