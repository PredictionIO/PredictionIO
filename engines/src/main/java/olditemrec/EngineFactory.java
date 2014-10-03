package io.prediction.engines.java.olditemrec;

import io.prediction.engines.java.olditemrec.data.TrainingData;
import io.prediction.engines.java.olditemrec.data.PreparedData;
import io.prediction.engines.java.olditemrec.data.Query;
import io.prediction.engines.java.olditemrec.data.Prediction;
import io.prediction.engines.java.olditemrec.data.Actual;
import io.prediction.engines.java.olditemrec.algos.GenericItemBased;
import io.prediction.engines.java.olditemrec.algos.SVDPlusPlus;

import io.prediction.controller.java.IJavaEngineFactory;
import io.prediction.controller.java.EmptyParams;
import io.prediction.controller.java.JavaEngine;
import io.prediction.controller.java.JavaEngineBuilder;

public class EngineFactory implements IJavaEngineFactory {
  public JavaEngine<TrainingData, EmptyParams, PreparedData, Query, Prediction, Actual> apply() {
    return new JavaEngineBuilder<
      TrainingData, EmptyParams, PreparedData, Query, Prediction, Actual>()
      .dataSourceClass(ItemRecDataSource.class)
      .preparatorClass(ItemRecPreparator.class)
      .addAlgorithmClass("genericitembased", GenericItemBased.class)
      .addAlgorithmClass("svdplusplus", SVDPlusPlus.class)
      .servingClass(ItemRecServing.class)
      .build();
  }
}
