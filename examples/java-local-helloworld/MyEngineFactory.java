package org.sample.java.helloworld;

import io.prediction.controller.java.*;

public class MyEngineFactory implements IJavaEngineFactory {
  public JavaSimpleEngine<MyTrainingData, EmptyDataParams, MyQuery, MyPrediction,
    EmptyActual> apply() {

    return new JavaSimpleEngineBuilder<MyTrainingData, EmptyDataParams,
      MyQuery, MyPrediction, EmptyActual> ()
      .dataSourceClass(MyDataSource.class)
      .preparatorClass() // Use default Preparator
      .addAlgorithmClass("", MyAlgorithm.class)
      .servingClass() // Use default Serving
      .build();
  }
}
