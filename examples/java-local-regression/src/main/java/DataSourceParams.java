package io.prediction.examples.java.regression;

import io.prediction.controller.java.JavaParams;

public class DataSourceParams implements JavaParams {
  public final String filepath;
  public DataSourceParams(String filepath) {
    this.filepath = filepath;
  }
}
