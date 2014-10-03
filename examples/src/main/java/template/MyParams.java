package myengine;

import io.prediction.controller.Params;

public class MyParams {

  public static class DataSourceParams implements Params {}

  public static class DataParams implements Params {}

  public static class PreparatorParams implements Params {}

  public static class AlgoParams implements Params {}

  public static class ServingParams implements Params {}

  public static class MetricsParams implements Params {}
}
