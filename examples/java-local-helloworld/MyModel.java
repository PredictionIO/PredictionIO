package org.sample.java.helloworld;

import java.io.Serializable;
import java.util.Map;

public class MyModel implements Serializable {
  Map<String, Double> temperatures;

  public MyModel(Map<String, Double> temperatures) {
    this.temperatures = temperatures;
  }

  @Override
  public String toString() {
    return temperatures.toString();
  }
}
