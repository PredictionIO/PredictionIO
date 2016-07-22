package org.apache.predictionio.examples.java.parallel;

import org.apache.predictionio.controller.java.IJavaEngineFactory;
import org.apache.predictionio.controller.java.LJavaFirstServing;
import org.apache.predictionio.controller.java.PJavaEngine;
import org.apache.predictionio.controller.java.PJavaEngineBuilder;

import java.util.HashMap;

import org.apache.spark.api.java.JavaPairRDD;

public class EngineFactory implements IJavaEngineFactory {
  public PJavaEngine<JavaPairRDD<String, Float>, Object, JavaPairRDD<String, Float>, Query, Float,
        Object> apply() {
    return new PJavaEngineBuilder<JavaPairRDD<String, Float>, Object, JavaPairRDD<String, Float>,
          Query, Float, Object> ()
      .dataSourceClass(DataSource.class)
      .preparatorClass(Preparator.class)
      .addAlgorithmClass("ParallelAlgorithm", Algorithm.class)
      .servingClass(Serving.class)
      .build();
  }
}
