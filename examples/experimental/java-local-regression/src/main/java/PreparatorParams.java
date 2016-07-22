package org.apache.predictionio.examples.java.regression;

import org.apache.predictionio.controller.java.JavaParams;

public class PreparatorParams implements JavaParams {
  // Take the r-fraction of data in training.
  public double r = 1.0;
  public PreparatorParams(double r) {
    this.r = r;
  }
}


