package myengine;

import io.prediction.controller.java.LJavaServing;

import myengine.MyParams.ServingParams;
import myengine.MyData.Query;
import myengine.MyData.Prediction;

public class Serving
  extends LJavaServing<ServingParams, Query, Prediction> {

  public Serving(ServingParams params) {

  }

  @Override
  public Prediction serve(Query query, Iterable<Prediction> predictions) {
    return new Prediction();
  }

}
