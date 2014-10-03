package myengine;

import io.prediction.controller.java.JavaMetrics;

import myengine.MyParams.MetricsParams;
import myengine.MyParams.DataParams;
import myengine.MyData.Query;
import myengine.MyData.Prediction;
import myengine.MyData.Actual;
import myengine.MyData.MetricUnit;
import myengine.MyData.MetricResult;
import myengine.MyData.MultipleMetricResult;

import scala.Tuple2;

public class Metrics
  extends JavaMetrics<MetricsParams, DataParams, Query, Prediction, Actual,
  MetricUnit, MetricResult, MultipleMetricResult> {

  public Metrics(MetricsParams params) {

  }

  @Override
  public MetricUnit computeUnit(Query query, Prediction predicted, Actual actual) {
    return new MetricUnit();
  }

  @Override
  public MetricResult computeSet(DataParams dataParams, Iterable<MetricUnit> metricUnits) {
    return new MetricResult();
  }

  @Override
  public MultipleMetricResult computeMultipleSets(
    Iterable<Tuple2<DataParams, MetricResult>> input) {
    return new MultipleMetricResult();
  }

}
