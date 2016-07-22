package org.sample.java.helloworld;

import org.apache.predictionio.controller.java.*;

public class MyEngineFactory implements IJavaEngineFactory {
  public JavaSimpleEngine<MyTrainingData, EmptyDataParams, MyQuery,
    MyPredictedResult, EmptyActualResult> apply() {

    return new JavaSimpleEngineBuilder<MyTrainingData, EmptyDataParams,
      MyQuery, MyPredictedResult, EmptyActualResult> ()
      .dataSourceClass(MyDataSource.class)
      .preparatorClass() // Use default Preparator
      .addAlgorithmClass("", MyAlgorithm.class)
      .servingClass() // Use default Serving
      .build();
  }
}
