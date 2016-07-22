package org.apache.predictionio.examples.java.recommendations.tutorial4;

import java.io.Serializable;
import java.util.Map;
import org.apache.commons.math3.linear.RealVector;

public class CollaborativeFilteringModel implements Serializable {
  public Map<Integer, RealVector> itemSimilarity;
  public Map<Integer, RealVector> userHistory;

  public CollaborativeFilteringModel(Map<Integer, RealVector> itemSimilarity,
    Map<Integer, RealVector> userHistory) {
    this.itemSimilarity = itemSimilarity;
    this.userHistory = userHistory;
  }

  @Override
  public String toString() {
    String s;
    if ((itemSimilarity.size() > 20) || (userHistory.size() > 20)) {
      s = "Model: [itemSimilarity.size=" + itemSimilarity.size() + "]\n"
        +"[userHistory.size=" + userHistory.size() + "]";
    } else {
      s = "Model: [itemSimilarity: " + itemSimilarity.toString() + "]\n"
      +"[userHistory: " + userHistory.toString() + "]";
    }
    return s;
  }
}
