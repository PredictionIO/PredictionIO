package io.prediction.examples.java.recommendations.tutorial2;

import io.prediction.examples.java.recommendations.tutorial1.TrainingData;
import io.prediction.examples.java.recommendations.tutorial1.Query;
import io.prediction.examples.java.recommendations.tutorial1.DataSource;
import io.prediction.examples.java.recommendations.tutorial1.DataSourceParams;
import io.prediction.examples.java.recommendations.tutorial1.Algorithm;
import io.prediction.examples.java.recommendations.tutorial1.AlgoParams;

import io.prediction.controller.java.EmptyParams;
import io.prediction.controller.java.IJavaEngineFactory;
import io.prediction.controller.java.JavaSimpleEngine;
import io.prediction.controller.java.JavaSimpleEngineBuilder;
import io.prediction.controller.java.JavaEngineParams;
import io.prediction.controller.java.JavaEngineParamsBuilder;
import io.prediction.controller.java.JavaWorkflow;
import io.prediction.controller.java.WorkflowParamsBuilder;

import java.util.HashMap;

import io.prediction.controller.IdentityPreparator;

public class Runner2 {

  // During development, one can build a semi-engine, only add the first few layers. In this
  // particular example, we only add until dataSource layer
  private static class HalfBakedEngineFactory implements IJavaEngineFactory {
    public JavaSimpleEngine<TrainingData, Object, Query, Float, Object> apply() {
      return new JavaSimpleEngineBuilder<
        TrainingData, Object, Query, Float, Object> ()
        .dataSourceClass(DataSource.class)
        .preparatorClass() // Use default Preparator
        .addAlgorithmClass("MyRecommendationAlgo", Algorithm.class) // Add Algorithm
        .build();
    }
  }

  public static void runComponents(String filePath) {
    JavaEngineParams engineParams = new JavaEngineParamsBuilder()
      .dataSourceParams(new DataSourceParams(filePath))
      .addAlgorithmParams("MyRecommendationAlgo", new AlgoParams(0.2)) // Add Algorithm Params
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
    if (args.length == 0) {
      System.out.println("Error: Please specify the file path as argument");
      System.exit(1);
    }
    runComponents(args[0]);
    System.exit(0); // clean shutdown is needed for spark
  }
}
