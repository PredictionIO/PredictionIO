package io.prediction.engines.java.olditemrec;

import io.prediction.controller.java.JavaParams;

public class MetricsParams implements JavaParams {
  public int k;

  public MetricsParams(int k) {
    this.k = k;
  }
}
