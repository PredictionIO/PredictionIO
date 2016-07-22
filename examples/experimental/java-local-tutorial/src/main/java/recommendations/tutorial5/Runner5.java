package org.apache.predictionio.examples.java.recommendations.tutorial5;

import org.apache.predictionio.examples.java.recommendations.tutorial1.DataSourceParams;
import org.apache.predictionio.examples.java.recommendations.tutorial3.Evaluator;

import org.apache.predictionio.controller.java.EmptyParams;
import org.apache.predictionio.controller.java.IJavaEngineFactory;
import org.apache.predictionio.controller.java.JavaSimpleEngine;
import org.apache.predictionio.controller.java.JavaSimpleEngineBuilder;
import org.apache.predictionio.controller.java.JavaEngineParams;
import org.apache.predictionio.controller.java.JavaEngineParamsBuilder;
import org.apache.predictionio.controller.java.JavaWorkflow;
import org.apache.predictionio.controller.java.WorkflowParamsBuilder;

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
      Evaluator.class,
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
