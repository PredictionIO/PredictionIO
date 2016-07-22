package org.apache.predictionio.examples.java.regression;

import org.apache.predictionio.controller.java.JavaParams;

public class DataSourceParams implements JavaParams {
  public final String filepath;
  public DataSourceParams(String filepath) {
    this.filepath = filepath;
  }
}
