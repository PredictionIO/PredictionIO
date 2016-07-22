package org.apache.predictionio.examples.java.regression;

import org.apache.predictionio.controller.java.IJavaEngineFactory;
import org.apache.predictionio.controller.java.JavaEngine;
import org.apache.predictionio.controller.java.JavaEngineBuilder;

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
