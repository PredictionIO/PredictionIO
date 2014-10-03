package io.prediction.engines.java.olditemrec;

import io.prediction.engines.java.olditemrec.data.Query;
import io.prediction.engines.java.olditemrec.data.Actual;
import io.prediction.engines.java.olditemrec.data.Prediction;
import io.prediction.engines.java.olditemrec.data.TrainingData;
import io.prediction.engines.java.olditemrec.data.PreparedData;
import io.prediction.engines.java.olditemrec.algos.GenericItemBased;
import io.prediction.engines.java.olditemrec.algos.GenericItemBasedParams;
import io.prediction.engines.java.olditemrec.algos.SVDPlusPlus;
import io.prediction.engines.java.olditemrec.algos.SVDPlusPlusParams;
import io.prediction.controller.java.JavaParams;
//import io.prediction.controller.java.EmptyParams;
import io.prediction.controller.java.LJavaAlgorithm;
import io.prediction.controller.java.JavaEngine;
import io.prediction.controller.java.JavaEngineBuilder;
import io.prediction.controller.java.JavaEngineParams;
import io.prediction.controller.java.JavaEngineParamsBuilder;
import io.prediction.controller.java.JavaWorkflow;
import io.prediction.controller.java.WorkflowParamsBuilder;

import io.prediction.controller.EmptyParams;

import io.prediction.controller.Params;

import scala.Tuple2;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

public class Runner {
  public static void main(String[] args) {

    String filePath = "src/main/java/itemrec/examples/ratings.csv";
    String algoName = "genericitembased";

    if (args.length == 2) {
      filePath = args[0];
      algoName = args[1];
    }

    System.out.println(Arrays.toString(args));
    int k = 20;
    DataSourceParams dsp = new DataSourceParams(filePath, 1, 0.8f, 0.2f, 0, 3, k);
    EmptyParams pp = new EmptyParams();
    GenericItemBasedParams genericItemBasedParams = new GenericItemBasedParams(10);
    SVDPlusPlusParams svdPlusPlusParams = new SVDPlusPlusParams(10);
    //ServingParams sp = new ServingParams();
    EmptyParams sp = new EmptyParams();

    String algo;
    //JavaParams algoParams;
    Params algoParams;
    if (algoName.equals("genericitembased")) {
      algo = "genericitembased";
      algoParams = genericItemBasedParams;
    } else {
      algo = "svdplusplus";
      algoParams = svdPlusPlusParams;
    }

    JavaEngineParams engineParams = new JavaEngineParamsBuilder()
      .dataSourceParams(dsp)
      .preparatorParams(pp)
      .addAlgorithmParams(algo, algoParams)
      .servingParams(sp)
      .build();

    JavaWorkflow.runEngine(
      (new EngineFactory()).apply(),
      engineParams,
      ItemRecMetrics.class,
      new MetricsParams(k),
      new WorkflowParamsBuilder().batch("Java Itemrec engine").verbose(3).build()
      );
  }

}
