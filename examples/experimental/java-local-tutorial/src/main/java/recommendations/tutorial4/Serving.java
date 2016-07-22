package org.apache.predictionio.examples.java.recommendations.tutorial4;

import org.apache.predictionio.controller.java.LJavaServing;
import org.apache.predictionio.controller.java.EmptyParams;
import java.lang.Iterable;

public class Serving extends LJavaServing<EmptyParams, Query, Float> {
  public Serving() {}

  public Float serve(Query query, Iterable<Float> predictions) {
    float sum = 0.0f;
    int count = 0;
    
    for (Float v: predictions) {
      if (!v.isNaN()) {
        sum += v;
        count += 1;
      }
    }
    return (count == 0) ? Float.NaN : sum / count;
  }
}

