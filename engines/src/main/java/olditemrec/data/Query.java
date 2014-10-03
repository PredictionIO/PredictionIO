package io.prediction.engines.java.olditemrec.data;

import java.io.Serializable;

public class Query implements Serializable {
  public int uid;
  public int n;

  public Query(int uid, int n) {
    this.uid = uid;
    this.n = n;
  }

  @Override
  public String toString() {
    return "Query: " + uid + ", " + n;
  }
}
