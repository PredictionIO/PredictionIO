package org.apache.predictionio.examples.java.recommendations.tutorial2;

import org.apache.predictionio.examples.java.recommendations.tutorial1.TrainingData;
import org.apache.predictionio.examples.java.recommendations.tutorial1.Query;
import org.apache.predictionio.examples.java.recommendations.tutorial1.DataSource;
import org.apache.predictionio.examples.java.recommendations.tutorial1.DataSourceParams;

import org.apache.predictionio.controller.java.EmptyParams;
import org.apache.predictionio.controller.java.IJavaEngineFactory;
import org.apache.predictionio.controller.java.JavaSimpleEngine;
import org.apache.predictionio.controller.java.JavaSimpleEngineBuilder;
import org.apache.predictionio.controller.java.JavaEngineParams;
import org.apache.predictionio.controller.java.JavaEngineParamsBuilder;
import org.apache.predictionio.controller.java.JavaWorkflow;
import org.apache.predictionio.controller.java.WorkflowParamsBuilder;
import java.util.HashMap;

public class Runner1 {

  // During development, one can build a semi-engine, only add the first few layers. In this
  // particular example, we only add until dataSource layer
  private static class HalfBakedEngineFactory implements IJavaEngineFactory {
    public JavaSimpleEngine<TrainingData, Object, Query, Float, Object> apply() {
      return new JavaSimpleEngineBuilder<
        TrainingData, Object, Query, Float, Object> ()
        .dataSourceClass(DataSource.class)
        .build();
    }
  }

  public static void runComponents(String filePath) {
    JavaEngineParams engineParams = new JavaEngineParamsBuilder()
      .dataSourceParams(new DataSourceParams(filePath))
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
