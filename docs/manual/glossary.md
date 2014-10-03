---
layout: docs
title: Glossary
---

**DataPreparator**
- Part of Engine. It reads data from source and transforms it to the desired
format.

**DataSource**
- Part of Engine. It preprocesses the data and forward it to the algorithm for
model training.

**Engine**
- An Engine represents a type of prediction, e.g. product recommendation. It is
comprised of four components: [D] Data Source and Data Preparator, [A]
Algorithm, [S] Serving, [E] Evaluation Metrics.

**Engine Instance**
- An Engine Instance is a deployable implementation of an Engine with defined
parameter settings. It specifies: one Data Source, one Data Preparator. one or
more algorithm(s), and one Serving. You can deploy one or more engine instances
from an engine at the same time.


**EngineClient**
- Part of PredictionSDK. It sends queries to a deployed engine instance through
the Engine API and retrives prediction results.

**Event API**
- Please see Event Server.

**Event Server**
- Event Server is designed to collect data into PredictionIO in an event-based
style. Once the Event Server is launched, your application can send data to it
through its Event API with HTTP requests or with the EventClient of
PredictionIO's SDKs.

**EventClient**
- Please see Event Server.

**Live Evaluation**
- Evaluation of prediction results in a production environment. Prediction
results are shown to real users. Users do not rate the results explicitly but
the system observes user behaviors such as click through rate.

**Offline Evaluation**
- The prediction results are compared with pre-compiled offline datasets.
Typically, offline evaluations are meant to identify the most promising
approaches.

**Test Data**
- Also commonly referred as Test Set. A set of data used to assess the strength
and utility of a predictive relationship.

**Training Data**
- Also commonly referred as Training Set. A set of data used to discover
potentially predictive relationships. In PredictionIO Engine, training data is
processed through the Data layer and passed onto algorithm.

