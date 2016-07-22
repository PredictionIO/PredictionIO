package org.apache.predictionio.examples.java.recommendations.tutorial5;

import org.apache.predictionio.controller.java.JavaParams;

public class MahoutAlgoParams implements JavaParams {

  String itemSimilarity;

  final static String LOG_LIKELIHOOD = "LogLikelihoodSimilarity";
  final static String TANIMOTO_COEFFICIENT = "TanimotoCoefficientSimilarity";
  
  public MahoutAlgoParams(String itemSimilarity) {
    this.itemSimilarity = itemSimilarity;
  }

}
