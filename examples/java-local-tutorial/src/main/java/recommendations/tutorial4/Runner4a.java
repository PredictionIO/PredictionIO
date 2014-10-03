package io.prediction.examples.java.recommendations.tutorial4;

import io.prediction.controller.java.EmptyParams;
import io.prediction.controller.java.IJavaEngineFactory;
import io.prediction.controller.java.JavaEngine;
import io.prediction.controller.java.JavaEngineBuilder;
import io.prediction.controller.java.JavaEngineParams;
import io.prediction.controller.java.JavaEngineParamsBuilder;
import io.prediction.controller.java.JavaWorkflow;
import io.prediction.controller.java.WorkflowParamsBuilder;

import java.util.HashMap;

import io.prediction.controller.IdentityPreparator;

public class Runner4a {
  
  private static class HalfBakedEngineFactory implements IJavaEngineFactory {
    public JavaEngine<TrainingData, EmptyParams, TrainingData, Query, Float, Object> apply() {
      return new JavaEngineBuilder<
        TrainingData, EmptyParams, TrainingData, Query, Float, Object> ()
        .dataSourceClass(DataSource.class)
        .build();
    }
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println("Error: Please specify the file directory as argument");
      System.exit(1);
    }

    JavaEngineParams engineParams = new JavaEngineParamsBuilder()
      .dataSourceParams(new DataSourceParams(args[0], true))
      .build();

    JavaWorkflow.runEngine(
      (new HalfBakedEngineFactory()).apply(),
      engineParams,
      new WorkflowParamsBuilder().batch("Recommendation.tutorial4.Runner4a").verbose(3).build()
    );
  }
}
