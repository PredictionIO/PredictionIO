package io.prediction.examples.java.recommendations.tutorial4;

import io.prediction.controller.java.EmptyParams;
import io.prediction.controller.java.IJavaEngineFactory;
import io.prediction.controller.java.JavaEngine;
import io.prediction.controller.java.JavaEngineBuilder;
import io.prediction.controller.java.JavaEngineParams;
import io.prediction.controller.java.JavaEngineParamsBuilder;
import io.prediction.controller.java.LJavaFirstServing;
import io.prediction.controller.java.JavaWorkflow;
import io.prediction.controller.java.WorkflowParamsBuilder;

import java.util.HashMap;

import io.prediction.controller.IdentityPreparator;

public class Runner4c {
  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println("Error: Please specify the file directory as argument");
      System.exit(1);
    }

    JavaEngineParams engineParams = new JavaEngineParamsBuilder()
      .dataSourceParams(new DataSourceParams(args[0], true))
      // 1 -> -1., 2 -> -.5, 3 -> 0., 4 -> .5, 5 -> 1.
      .addAlgorithmParams("featurebased", new FeatureBasedAlgorithmParams(1.0, 5.0, 3.0, 0.5))
      .build();

    JavaWorkflow.runEngine(
      (new SingleEngineFactory()).apply(),
      engineParams,
      new WorkflowParamsBuilder().batch("Recommendation.tutorial4.Runner4c").verbose(3).build()
    );
  }
}


