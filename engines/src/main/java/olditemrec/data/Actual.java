package io.prediction.engines.java.olditemrec.data;

import java.io.Serializable;
import java.util.Set;

public class Actual implements Serializable {
  public Set<Integer> iids;

  public Actual(Set<Integer> iids) {
    this.iids = iids;
  }

  @Override
  public String toString() {
    return "Actual: " + iids.toString();
  }
}
