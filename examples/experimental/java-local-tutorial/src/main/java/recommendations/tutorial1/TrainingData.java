package org.apache.predictionio.examples.java.recommendations.tutorial1;

import java.io.Serializable;
import java.util.List;

public class TrainingData implements Serializable {
  public List<Rating> ratings;

  public TrainingData(List<Rating> ratings) {
    this.ratings = ratings;
  }

  @Override
  public String toString() {
    String s;
    if (ratings.size() > 20)
      s = "TrainingData.size=" + ratings.size();
    else
      s = ratings.toString();
    return s;
  }

  public static class Rating implements Serializable {
    public int uid; // user ID
    public int iid; // item ID
    public float rating;

    public Rating(int uid, int iid, float rating) {
      this.uid = uid;
      this.iid = iid;
      this.rating = rating;
    }

    @Override
    public String toString() {
      return "(" + uid + "," + iid + "," + rating + ")";
    }
  }
}
