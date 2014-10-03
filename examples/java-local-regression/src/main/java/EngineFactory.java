package io.prediction.examples.java.regression;

import io.prediction.controller.java.IJavaEngineFactory;
import io.prediction.controller.java.JavaEngine;
import io.prediction.controller.java.JavaEngineBuilder;

public class EngineFactory implements IJavaEngineFactory {
  public JavaEngine<TrainingData, Integer, TrainingData, Double[], Double, Double> apply() {
    return new JavaEngineBuilder<TrainingData, Integer, TrainingData, Double[], Double, Double> ()
      .dataSourceClass(DataSource.class)
      .preparatorClass(Preparator.class)
      .addAlgorithmClass("OLS", OLSAlgorithm.class)
      .addAlgorithmClass("Default", DefaultAlgorithm.class)
      .servingClass(Serving.class)
      .build();
  }
}
