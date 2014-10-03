package io.prediction.engines.java.olditemrec.data;

import java.io.Serializable;
import java.util.Map;
import java.util.List;

import org.apache.mahout.cf.taste.recommender.RecommendedItem;

/**
 * Simple model that cache recommendation outputs
 */
public class Model implements Serializable {
  // uid -> List<iid, score> sorted by iid
  public Map<Long, List<RecommendedItem>> itemRecScores;

  public Model(Map<Long, List<RecommendedItem>> itemRecScores) {
    this.itemRecScores = itemRecScores;
  }

  @Override
  public String toString() {
    return "Model: " +itemRecScores.toString();
  }
}
