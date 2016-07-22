package org.apache.predictionio.examples.java.recommendations.tutorial4;

import java.io.Serializable;
import java.util.Map;
import org.apache.commons.math3.linear.RealVector;

public class FeatureBasedModel implements Serializable {
  // Feature value is always between -1 and 1.
  public final Map<Integer, RealVector> userFeatures;
  public final Map<Integer, Integer> userActions;
  public final Map<Integer, RealVector> itemFeatures;

  public FeatureBasedModel(
      Map<Integer, RealVector> userFeatures,
      Map<Integer, Integer> userActions,
      Map<Integer, RealVector> itemFeatures) {
    this.userFeatures = userFeatures;
    this.userActions = userActions;
    this.itemFeatures = itemFeatures;
  }
}

