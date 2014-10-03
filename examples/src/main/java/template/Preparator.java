package myengine;

import io.prediction.controller.java.LJavaPreparator;

import myengine.MyParams.PreparatorParams;
import myengine.MyData.TrainingData;
import myengine.MyData.PreparedData;

public class Preparator extends
  LJavaPreparator<PreparatorParams, TrainingData, PreparedData> {

  public Preparator(PreparatorParams params) {

  }

  @Override
  public PreparedData prepare(TrainingData data) {
    return new PreparedData();
  }
}
