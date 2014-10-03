package myengine;

import java.io.Serializable;

public class MyData {

  // engine
  public static class TrainingData implements Serializable {}

  public static class Query implements Serializable {}

  public static class Actual implements Serializable {}

  public static class Prediction implements Serializable {}

  public static class PreparedData implements Serializable {}

  public static class Model implements Serializable {}

  // metrics
  public static class MetricUnit implements Serializable {}

  public static class MetricResult implements Serializable {}

  public static class MultipleMetricResult implements Serializable {}

}
