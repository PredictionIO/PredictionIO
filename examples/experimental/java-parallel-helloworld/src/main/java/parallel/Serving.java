package org.apache.predictionio.examples.java.parallel;

import org.apache.predictionio.controller.java.EmptyParams;
import org.apache.predictionio.controller.java.LJavaServing;

public class Serving extends LJavaServing<EmptyParams, Query, Float> {

  public Serving() {

  }

  @Override
  public Float serve(Query query, Iterable<Float> predictions) {
    return predictions.iterator().next();
  }

}
