package org.apache.predictionio.examples.java.regression;

import java.io.Serializable;
import java.util.Arrays;

public class TrainingData implements Serializable {
  public final Double[][] x;
  public final Double[] y;
  public final int r;
  public final int c;
  public TrainingData(Double[][] x, Double[] y) {
    this.x = x;
    this.y = y;
    this.r = x.length;
    this.c = x[0].length;
  }
  @Override public String toString() {
    return "TrainingData: r=" + this.r + ",c=" + this.c;
  }
}
