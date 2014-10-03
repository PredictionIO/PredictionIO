package org.sample.java.helloworld;

import java.io.Serializable;
import java.util.List;

public class MyTrainingData implements Serializable {
  List<DayTemperature> temperatures;

  public MyTrainingData(List<DayTemperature> temperatures) {
    this.temperatures = temperatures;
  }

  public static class DayTemperature implements Serializable {
    String day;
    Double temperature;

    public DayTemperature(String day, Double temperature) {
      this.day = day;
      this.temperature = temperature;
    }
  }
}
