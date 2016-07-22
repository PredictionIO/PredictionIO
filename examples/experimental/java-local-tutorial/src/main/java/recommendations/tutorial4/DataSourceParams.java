package org.apache.predictionio.examples.java.recommendations.tutorial4;

import org.apache.predictionio.controller.java.JavaParams;

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
