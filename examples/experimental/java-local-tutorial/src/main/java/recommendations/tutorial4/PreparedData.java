package org.apache.predictionio.examples.java.recommendations.tutorial4;

import java.util.Map;
import org.apache.commons.math3.linear.RealVector;

public class PreparedData extends TrainingData {
  public final Map<Integer, RealVector> itemFeatures;
  public final int featureCount;

  public PreparedData(TrainingData data, Map<Integer, RealVector> itemFeatures, int featureCount) {
    super(data);
    this.itemFeatures = itemFeatures;
    this.featureCount = featureCount;
  }

  @Override
  public String toString() {
    return super.toString() + " itemFeatures.size=" + itemFeatures.size()
      + " featureCount=" + featureCount;
  }
}
