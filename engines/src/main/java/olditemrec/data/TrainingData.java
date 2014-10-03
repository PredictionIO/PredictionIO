package io.prediction.engines.java.olditemrec.data;

import java.io.Serializable;
import java.util.List;
import org.apache.mahout.cf.taste.model.DataModel;

public class TrainingData implements Serializable {
  public List<Rating> ratings;

  // TODO: add more other trainiig data, such as item types data, user data, etc
  public TrainingData(List<Rating> ratings) {
    this.ratings = ratings;
  }
}
