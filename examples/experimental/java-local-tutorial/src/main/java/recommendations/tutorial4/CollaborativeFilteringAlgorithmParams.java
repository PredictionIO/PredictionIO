package org.apache.predictionio.examples.java.recommendations.tutorial4;

import org.apache.predictionio.controller.java.JavaParams;

public class CollaborativeFilteringAlgorithmParams implements JavaParams {
  public double threshold;

  public CollaborativeFilteringAlgorithmParams(double threshold) {
    this.threshold = threshold;
  }
}
