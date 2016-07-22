package org.apache.predictionio.examples.java.regression;

import org.apache.predictionio.controller.java.LJavaAlgorithm;

// This algorithm is for illustration only. It returns a constant.
public class DefaultAlgorithm
  extends LJavaAlgorithm<DefaultAlgorithmParams, TrainingData, Object, Double[], Double> {
  public final DefaultAlgorithmParams p; 

  public DefaultAlgorithm(DefaultAlgorithmParams p) {
    this.p = p;
  }

  public Object train(TrainingData data) {
    return null;
  }

  public Double predict(Object nullModel, Double[] query) {
    return p.v;
  }
}

