package io.prediction.engines.java.olditemrec;

import io.prediction.controller.java.LJavaServing;
import io.prediction.engines.java.olditemrec.data.Query;
import io.prediction.engines.java.olditemrec.data.Prediction;

import io.prediction.controller.EmptyParams;

//public class ItemRecServing extends LJavaServing<ServingParams, Query, Prediction> {
public class ItemRecServing extends LJavaServing<EmptyParams, Query, Prediction> {

  /*
  ServingParams params;

  public ItemRecServing(ServingParams params) {
    this.params = params;
  }
  */

  @Override
  public Prediction serve(Query query, Iterable<Prediction> predictions) {
    // TODO: support combine multiple algo output
    return predictions.iterator().next();
  }
}
