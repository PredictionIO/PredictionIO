package io.prediction.examples.java.regression;

import io.prediction.controller.java.JavaMetrics;
import java.lang.Iterable;
import io.prediction.controller.java.EmptyParams;
import scala.Tuple2;
import java.util.List;
import java.util.ArrayList;


public class MeanSquareMetrics
  extends JavaMetrics<EmptyParams, Integer,
          Double[], Double, Double, Double, Double, String> {

  public Double computeUnit(Double[] query, Double prediction, Double actual) {
    return prediction - actual;
  }

  public Double computeSet(Integer dp, Iterable<Double> mseSeq) {
    double mse = 0.0;
    int n = 0;
    for (Double e: mseSeq) {
      mse += e;
      n += 1;
    }
    return mse / n;
  }

  public String computeMultipleSets(
      Iterable<Tuple2<Integer, Double>> input) {
    List<String> l = new ArrayList<String>();
    for (Tuple2<Integer, Double> t : input) {
      l.add("MSE: " + t._2().toString());
    }
    return l.toString();
  }
}
