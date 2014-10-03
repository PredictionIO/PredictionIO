---
layout: docs
title: Engine Builders' Guide
---

# Engine Builders' Guide

## DASE Architecture Overview

A PredictionIO engine workflow looks like the following figure:

```
            DataSource.read
             (TrainingData)
                   v
           Preparator.prepare
             (PreparedData)
                   v
     +-------------+-------------+
     v             v             v
Algo1.train   Algo2.train   Algo3.train
  (Model1)      (Model2)      (Model3)
     v             v             v
Algo1.predict Algo2.predict Algo3.predict <- (Query)
(Prediction1) (Prediction2) (Prediction3)
     v             v             v
     +-------------+-------------+
                   v
              Serving.serve
              (Prediction)
```


A prediction **Engine** consists of the following controller components:
**DataSource**, **Data Preparator**(Preparator), **Algorithm**, and **Serving**.
Another component **Evaluation Metrics** is used to evaluate the *Engine*.

- *DataSource* is responsible for reading data from the source (Eg. database or
  text file, etc) and prepare the **Training Data (TD)**.
- *Data Preparator* takes the *Training Data* and generates **Prepared Data
  (PD)** for the *Algorithm*
- *Algorithm* takes the *Prepared Data* to train a **Model (M)** which is used
  make **Prediction (P)** outputs based on input **Query (Q)**.
- *Serving* serves the input *Query* with *Algorithm*'s *Prediction* outputs.

An **Engine Factory** is a factory which returns an *Engine* with the above
components defined.

To evaluate a prediction *Engine*:
- *DataSource* can also generate *Test Data* which is a list of input *Query*
  and **Actual (A)** result.
- *Metrics* computes the **Metric Result (MR)** by comparing the *Prediction*
  output with the *Actual* result. PredictionIO feeds the input *Query* to the
  *Engine* to get the *Prediction* outputs which are compared with *Actual*
  results.

As you can see, the controller components (*DataSource, Preparator, Algorithm,
Serving and Metrics*) are the building blocks of the data pipeline and the data
types (*Training Data*, *Prepared Data*, *Model*, *Query*, *Prediction* and
*Actual*) defines the types of data being passed between each component.

> Note that if the *Algorithm* can directly use *Training Data* without any
pre-processing, the *Prepared Data* can be the same as *Training Data* and a
default **Identity Preparator** can be used, which simply passes the *Training
Data* as *Prepared Data*.
>
>Also, if there is only one *Algorithm* in the *Engine*, and there is no special
post-processing on the *Prediction* outputs, a default **First Serving** can be
use, which simply uses the *Algorithm*'s *Prediction* output to serve the query.


It's time to build your first [HelloWorld
Engine]({{ site.baseurl }}/tutorials/enginebuilders/local-helloworld.html)!
