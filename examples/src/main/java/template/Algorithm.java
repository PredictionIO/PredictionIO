package myengine;

import io.prediction.controller.java.LJavaAlgorithm;

import myengine.MyParams.AlgoParams;
import myengine.MyData.PreparedData;
import myengine.MyData.Model;
import myengine.MyData.Query;
import myengine.MyData.Prediction;

public class Algorithm extends
  LJavaAlgorithm<AlgoParams, PreparedData, Model, Query, Prediction> {

  public Algorithm(AlgoParams params) {

  }

  @Override
  public Model train(PreparedData data) {
    return new MyData.Model();
  }

  @Override
  public Prediction predict(Model model, Query query) {
    return new Prediction();
  }
}
