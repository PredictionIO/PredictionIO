package org.apache.predictionio.examples.java.parallel;

import org.apache.predictionio.controller.IEngineFactory;
import org.apache.predictionio.controller.java.EmptyParams;
import org.apache.predictionio.controller.java.IJavaEngineFactory;
import org.apache.predictionio.controller.java.JavaEngineParams;
import org.apache.predictionio.controller.java.JavaEngineParamsBuilder;
import org.apache.predictionio.controller.java.JavaWorkflow;
import org.apache.predictionio.controller.java.PJavaEngine;
import org.apache.predictionio.controller.java.PJavaEngineBuilder;
import org.apache.predictionio.controller.java.WorkflowParamsBuilder;

import java.util.HashMap;

import org.apache.spark.api.java.JavaPairRDD;

public class Runner {

  // During development, one can build a semi-engine, only add the first few layers. In this
  // particular example, we only add until dataSource layer
  private static class HalfBakedEngineFactory implements IJavaEngineFactory {
    public PJavaEngine<JavaPairRDD<String, Float>, Object, JavaPairRDD<String, Float>,
        Query, Float, Object> apply() {
      return new PJavaEngineBuilder<
        JavaPairRDD<String, Float>, Object, JavaPairRDD<String, Float>, Query, Float, Object> ()
        .dataSourceClass(DataSource.class)
        .preparatorClass(Preparator.class)
        .addAlgorithmClass("ParallelAlgorithm", Algorithm.class)
        .servingClass(Serving.class)
        .build();
    }
  }

  public static void runComponents() {
    JavaEngineParams engineParams = new JavaEngineParamsBuilder()
      .addAlgorithmParams("ParallelAlgorithm", new EmptyParams())
      .servingParams(new EmptyParams())
      .build();
    JavaWorkflow.runEngine(
      (new HalfBakedEngineFactory()).apply(),
      engineParams,
      null,
      new EmptyParams(),
      new WorkflowParamsBuilder().batch("MyEngine").verbose(3).build()
    );
  }

  public static void main(String[] args) {
    runComponents();
  }
}
