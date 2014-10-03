---
layout: docs
title: Step-by-Step Engine Building
---

# Step-by-Step Engine Building

## Overview

These series of tutorials will walk through each components of **PredictionIO**.
We will demonstrate how to develop your machine learning algorithms and
prediction engines, deploy them and serve real time prediction queries, develop
your metrics to run offline evaluations, and improve prediction engine by using
multiple algoritms.

> You need to build PredictionIO from source in order to build your own engine.
Please follow instructions to build from source
[here]({{site.baseurl}}/install/install-sourcecode.html).

Let's build a simple **Java single machine recommendation engine** which
predicts item's rating value rated by the user. [MovieLens
100k](http://grouplens.org/datasets/movielens/) data set will be used as an
example.

Execute the following command to download MovieLens 100k to `data/ml-100k/`.

```
$ cd $PIO_HOME/examples/java-local-tutorial
$ ./fetch.sh
```
where `$PIO_HOME` is the root directory of the PredictionIO code tree.

In this first tutorial, we will demonstrate how to build an simple Item
Recommendation Engine with the *DataSource* and *Algorithm* components. You can
find all sources code of this tutorial in the directory
`java-local-tutorial/src/main/java/recommendations/tutorial1/`.

## Getting Started

Let's begin with [implementing a new Engine with Data and
Algorithm](dataalgorithm.html).
