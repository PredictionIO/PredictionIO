package org.apache.predictionio.examples.java.recommendations.tutorial4;

import org.apache.predictionio.controller.java.LJavaPreparator;
import org.apache.predictionio.controller.java.EmptyParams;

import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

public class Preparator extends LJavaPreparator<EmptyParams, TrainingData, PreparedData> {

  final static Logger logger = LoggerFactory.getLogger(Preparator.class);
  final int indexOffset = 5;

  public Preparator() {}

  public PreparedData prepare(TrainingData trainingData) {
    Map<Integer, RealVector> itemFeatures = new HashMap<Integer, RealVector>();

    int featureSize = trainingData.genres.size();

    for (Integer iid: trainingData.itemInfo.keySet()) {
      String[] info = trainingData.itemInfo.get(iid);

      RealVector features = new ArrayRealVector(featureSize);
      for (int i = 0; i < featureSize; i++) {
        features.setEntry(i, Double.parseDouble(info[i + indexOffset]));
      }
      itemFeatures.put(iid, features);
    }

    return new PreparedData(trainingData, itemFeatures, featureSize);
  }
}
