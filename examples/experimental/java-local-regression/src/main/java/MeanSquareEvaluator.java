package org.apache.predictionio.examples.java.regression;

import org.apache.predictionio.controller.java.JavaEvaluator;
import java.lang.Iterable;
import org.apache.predictionio.controller.java.EmptyParams;
import scala.Tuple2;
import java.util.List;
import java.util.ArrayList;


public class MeanSquareEvaluator
  extends JavaEvaluator<EmptyParams, Integer,
          Double[], Double, Double, Double, Double, String> {

  public Double evaluateUnit(Double[] query, Double prediction, Double actual) {
    return (prediction - actual) * (prediction - actual);
  }

  public Double evaluateSet(Integer dp, Iterable<Double> mseSeq) {
    double mse = 0.0;
    int n = 0;
    for (Double e: mseSeq) {
      mse += e;
      n += 1;
    }
    return mse / n;
  }

  public String evaluateAll(Iterable<Tuple2<Integer, Double>> input) {
    List<String> l = new ArrayList<String>();
    for (Tuple2<Integer, Double> t : input) {
      l.add("MSE: " + t._2().toString());
    }
    return l.toString();
  }
}
