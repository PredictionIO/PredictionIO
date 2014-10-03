package io.prediction.examples.java.recommendations.tutorial4;

import io.prediction.controller.java.EmptyParams;
import io.prediction.controller.java.IJavaEngineFactory;
import io.prediction.controller.java.JavaEngine;
import io.prediction.controller.java.JavaEngineBuilder;
import io.prediction.controller.java.LJavaFirstServing;

public class SingleEngineFactory implements IJavaEngineFactory {
  public JavaEngine<TrainingData, EmptyParams, PreparedData, Query, Float, Object> apply() {
    return new JavaEngineBuilder<
      TrainingData, EmptyParams, PreparedData, Query, Float, Object> ()
      .dataSourceClass(DataSource.class)
      .preparatorClass(Preparator.class)
      .addAlgorithmClass("featurebased", FeatureBasedAlgorithm.class)
      .servingClass(LJavaFirstServing.apply(FeatureBasedAlgorithm.class))
      .build();
  }
}
