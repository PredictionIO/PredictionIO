package org.apache.predictionio.examples.java.recommendations.tutorial3;

import org.apache.predictionio.examples.java.recommendations.tutorial1.Query;
import org.apache.predictionio.controller.java.JavaEvaluator;
import org.apache.predictionio.controller.java.EmptyParams;

import scala.Tuple2;
import java.util.Arrays;
import org.apache.commons.collections.IteratorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Root mean square error */
public class Evaluator
  extends JavaEvaluator<EmptyParams, Object, Query, Float, Float,
  Double, Double, String> {

  final static Logger logger = LoggerFactory.getLogger(Evaluator.class);

  @Override
  public Double evaluateUnit(Query query, Float predicted, Float actual) {
    logger.info("Q: " + query.toString() + " P: " + predicted + " A: " + actual);
    // return squared error
    double error;
    if (predicted.isNaN())
      error = -actual;
    else
      error = predicted - actual;
    return (error * error);
  }

  @Override
  public Double evaluateSet(Object dataParams, Iterable<Double> metricUnits) {
    double sum = 0.0;
    int count = 0;
    for (double squareError : metricUnits) {
      sum += squareError;
      count += 1;
    }
    return Math.sqrt(sum / count);
  }

  @Override
  public String evaluateAll(
    Iterable<Tuple2<Object, Double>> input) {
    return Arrays.toString(IteratorUtils.toArray(input.iterator()));
  }
}
