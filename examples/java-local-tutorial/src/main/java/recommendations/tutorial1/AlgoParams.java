package io.prediction.examples.java.recommendations.tutorial1;

import io.prediction.controller.java.JavaParams;

public class AlgoParams implements JavaParams {
  public double threshold;

  public AlgoParams(double threshold) {
    this.threshold = threshold;
  }
}
