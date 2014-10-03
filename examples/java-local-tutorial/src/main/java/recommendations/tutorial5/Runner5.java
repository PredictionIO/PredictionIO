package io.prediction.examples.java.recommendations.tutorial5;

import io.prediction.examples.java.recommendations.tutorial1.DataSourceParams;
import io.prediction.examples.java.recommendations.tutorial3.Metrics;

import io.prediction.controller.java.EmptyParams;
import io.prediction.controller.java.IJavaEngineFactory;
import io.prediction.controller.java.JavaSimpleEngine;
import io.prediction.controller.java.JavaSimpleEngineBuilder;
import io.prediction.controller.java.JavaEngineParams;
import io.prediction.controller.java.JavaEngineParamsBuilder;
import io.prediction.controller.java.JavaWorkflow;
import io.prediction.controller.java.WorkflowParamsBuilder;

import java.util.HashMap;

public class Runner5 {

  public static void runEvaluation(String filePath) {
    JavaEngineParams engineParams = new JavaEngineParamsBuilder()
      .dataSourceParams(new DataSourceParams(filePath))
      .addAlgorithmParams("MyMahoutRecommendationAlgo",
        new MahoutAlgoParams("LogLikelihoodSimilarity"))
      .build();

    JavaWorkflow.runEngine(
      (new EngineFactory()).apply(),
      engineParams,
      Metrics.class,
      new EmptyParams(),
      new WorkflowParamsBuilder().batch("MyEngine").verbose(3).build()
    );
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println("Error: Please specify the file path as argument");
      System.exit(1);
    }
    runEvaluation(args[0]);
    System.exit(0); // clean shutdown is needed for spark
  }
}
