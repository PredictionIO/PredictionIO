package io.prediction.examples.java.recommendations.tutorial4;

import io.prediction.controller.java.JavaParams;

public class DataSourceParams implements JavaParams {
  public String dir;
  public boolean addFakeData;

  public DataSourceParams(String dir, boolean addFakeData) {
    this.dir = dir;
    this.addFakeData = addFakeData;
  }

  public DataSourceParams(String dir) {
    this(dir, false);
  }
}
