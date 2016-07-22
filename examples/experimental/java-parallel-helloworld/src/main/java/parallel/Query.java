package org.apache.predictionio.examples.java.parallel;

import java.io.Serializable;

public class Query implements Serializable {
  public String day;

  public Query(String day) {
    this.day = day;
  }
}
