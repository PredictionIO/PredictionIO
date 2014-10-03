package myengine;

import io.prediction.controller.IEngineFactory;
import io.prediction.controller.java.JavaEngine;
import io.prediction.controller.java.JavaEngineBuilder;
import io.prediction.controller.java.JavaEngineParams;
import io.prediction.controller.java.JavaEngineParamsBuilder;
import io.prediction.controller.java.JavaWorkflow;
import io.prediction.controller.java.WorkflowParamsBuilder;

import myengine.MyParams.DataSourceParams;
import myengine.MyParams.DataParams;
import myengine.MyParams.PreparatorParams;
import myengine.MyParams.AlgoParams;
import myengine.MyParams.ServingParams;
import myengine.MyParams.MetricsParams;
import myengine.MyData.TrainingData;
import myengine.MyData.PreparedData;
import myengine.MyData.Query;
import myengine.MyData.Prediction;
import myengine.MyData.Actual;

import java.util.HashMap;

public class Runner {

  // During development, one can build a semi-engine, only add the first few layers. In this
  // particular example, we only add until dataSource layer
  private static class HalfBakedEngineFactory implements IEngineFactory {
    public JavaEngine<TrainingData, DataParams, PreparedData, Query, Prediction, Actual> apply() {
      return new JavaEngineBuilder<
        TrainingData, DataParams, PreparedData, Query, Prediction, Actual> ()
        .dataSourceClass(DataSource.class)
        .build();
    }
  }

  public static void runComponents() {
    JavaEngineParams engineParams = new JavaEngineParamsBuilder()
      .dataSourceParams(new DataSourceParams())
      .preparatorParams(new PreparatorParams())
      .addAlgorithmParams("AlgorithmName", new AlgoParams())
      .servingParams(new ServingParams())
      .build();

    JavaWorkflow.runEngine(
      (new HalfBakedEngineFactory()).apply(),
      engineParams,
      Metrics.class,
      new MetricsParams(),
      new WorkflowParamsBuilder().batch("MyEngine").verbose(3).build()
    );
  }

  public static void runEngine() {
    JavaEngineParams engineParams = new JavaEngineParamsBuilder()
      .dataSourceParams(new DataSourceParams())
      .preparatorParams(new PreparatorParams())
      .addAlgorithmParams("AlgorithmName", new AlgoParams())
      .servingParams(new ServingParams())
      .build();

    JavaWorkflow.runEngine(
      (new EngineFactory()).apply(),
      engineParams,
      Metrics.class,
      new MetricsParams(),
      new WorkflowParamsBuilder().batch("MyEngine").verbose(3).build()
    );

  }
  public static void main(String[] args) {
    runEngine();
    //runComponents();
  }
}
