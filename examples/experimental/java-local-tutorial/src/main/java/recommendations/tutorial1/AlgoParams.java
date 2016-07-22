package org.apache.predictionio.examples.java.recommendations.tutorial1;

import org.apache.predictionio.controller.java.JavaParams;

public class AlgoParams implements JavaParams {
  public double threshold;

  public AlgoParams(double threshold) {
    this.threshold = threshold;
  }
}
