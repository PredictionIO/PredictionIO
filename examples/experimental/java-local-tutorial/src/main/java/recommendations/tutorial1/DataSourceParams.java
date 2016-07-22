package org.apache.predictionio.examples.java.recommendations.tutorial1;

import org.apache.predictionio.controller.java.JavaParams;

public class DataSourceParams implements JavaParams {
  public String filePath; // file path

  public DataSourceParams(String path) {
    this.filePath = path;
  }
}
