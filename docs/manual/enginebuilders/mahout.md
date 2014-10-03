---
layout: docs
title: Integrate Mahout's Recommendation Algorithm
---

# Integrate Mahout's Recommendation Algorithm

This example re-uses the *DataSource*, *Metrics* components shown in [Step-by-step Engine Building](../tutorials/enginebuilders/stepbystep/index.html) tutorials and integrates [Mahout](https://mahout.apache.org/)'s `GenericItemBasedRecommender` to build a recommendation engine.

This example also demonstrates that if the *Model* contains an unserializable object (Mahout `Recommender` in this case), the *Model* class needs to implement the `KryoSerializable` with `write()` and `read()` methods which recovers the unserializable object. The implementation can be found in
[MahoutAlgoModel.java](https://github.com/PredictionIO/PredictionIO/blob/master/examples/java-local-tutorial/src/main/java/recommendations/tutorial5/MahoutAlgoModel.java).
