package io.prediction.examples.java.regression;

import io.prediction.controller.java.JavaParams;

public class DefaultAlgorithmParams implements JavaParams {
  public final double v;
  public DefaultAlgorithmParams(double v) {
    this.v = v;
  }

  @Override
  public String toString() {
    return "DefaultAlgorithmParams (k=" + this.v + ")";
  }
}


