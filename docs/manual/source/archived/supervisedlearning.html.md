---
title: Machine Learning With PredictionIO
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

This guide is designed to give developers a brief introduction to fundamental concepts in machine learning, as well as an explanation of how these concept tie into PredictionIO's engine development platform. This particular guide will largely deal with giving some

## Introduction to Supervised Learning

The first question we must ask is: what is machine learning? **Machine learning** is the field of study at the intersection of computer science, engineering, mathematics, and statistics which seeks to discover or infer patterns hidden within a set of observations, which we call our data. Some examples of problems that machine learning seeks to solve are:



- Predict whether a patient has breast cancer based on their mammogram results.
- Predict whether an e-mail is spam or not based on the e-mail's content.
- Predict today's temperature based on climate variables collected for the previous week.

### Thinking About Data

In the latter examples, we are trying to predict an outcome \\(Y\\), or **response**, based on some recorded or observed variables \\(X\\), or **features**. For example: in the third problem each observation is a patient, the response variable \\(Y\\) is equal to 1 if this patient has breast cancer and 0 otherwise, and \\(X\\) represents the mammogram results.

When we say we want to predict \\(Y\\) using \\(X\\), we are trying to answer the question: how does a response \\(Y\\) depend on a set of features \\(X\\) affect the response \\(Y\\)? To do this we need a set of observations, which we call our **training data**, consisting of observations for which we have observed both \\(Y\\) and \\(X\\), in order to make inference about this relationship.

### Different Types of Supervised Learning Problems

Note that  in the first two examples, the outcome \\(Y\\) can  only take on two values (1 : cancer/spam, 0: no cancer/ no spam). Whenever the outcome variable \\(Y\\) denotes a label associated to a particular group of observations (i.e. cancer group), the **supervised learning** problem is also called a **classification** problem. In the third example, however, \\(Y\\) can take on any numerical value since it denotes some temperature reading (i.e. 25.143, 25.14233, 32.0). These types of supervised learning problems are also called **regression** problems.

### Training a Predictive Model

A predictive model should be thought of as a function \\(f\\) that takes as input a set of features, and outputs a predicted outcome (i.e. \\(f(X) = Y\\)). The phrase **training a model** simply refers to the process of using the training data to estimate such a function.  

## PredictionIO and Supervised Learning

Machine learning methods generally assume that our observation responses and features are numeric vectors. We will say that observations in this format are in **standard form**. However, when you are working with real-life data this will often not be the case. The data will often be formatted in a manner that is specific to the application's needs. As an example, let's suppose our application is [StackOverFlow](http://stackoverflow.com). The data we want to analyze are questions, and we want to predict based on a question's content whether or not it is related to Scala.


**Self-check:**   Is this a classification or regression problem?

### Thinking About Data With PredictionIO

PredictionIO's predictive engine development platform allows you to easily incorporate observations that are not in standard form. Continuing with our example, we can import the observations, or StackOverFlow questions, into [PredictionIO's Event Server](/datacollection/) as events with the following properties:


`properties = {question : String, topic : String}`

The value `question` is the actual question stored as a `String`, and topic is also a string equal to either `"Scala"` or `"Other"`. Our outcome here is `topic`, and `question` will provide a source for extracting features. That is, we will be using `question` to predict the outcome `topic`.

Once the observations are loaded as events into the Event Server, the engine's [Data Source](/customize/) component is able to read them,  which allows you to treat them as objects in a Scala project. The engine's Preparator component is in charge of converting these observations into standard form. To do this, we can first map the topic values as follows:



`Map("Other" -> 0, "Scala" -> 1)`.


We can then vectorize the observation's associated question text to obtain a numeric feature vector for each of our observations. This text vectorization procedure is an example of a general concept in machine learning called **feature extraction**. After performing these transformations of our observations, they are now in standard form and can be used for training a large quantity of machine learning models.

### Training the Model With PredictionIO

The Algorithm engine component serves two purposes: outputting a predictive model \\(f\\) and using this to predict the outcome variable. Here \\(f\\) takes as input a vectorized question and outputs either 0 or 1. However, our `Query` input will be again a question, and our `PredictedResult` the topic associated to the predicted label (0 or 1):


`Query = {question : String}`
`PredictedResult = {topic : String}`


With PredictionIO's engine development platform, you can easily automate the vectorization of the Query question, as well as mapping the predicted label to the appropriate topic output format.
