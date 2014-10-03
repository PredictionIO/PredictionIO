package org.sample.java.helloworld;

import java.io.Serializable;

public class MyPrediction implements Serializable {
  Double temperature;

  public MyPrediction(Double temperature) {
    this.temperature = temperature;
  }
}
