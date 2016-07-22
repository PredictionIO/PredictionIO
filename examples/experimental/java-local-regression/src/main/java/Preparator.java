package org.apache.predictionio.examples.java.regression;

import org.apache.predictionio.controller.java.LJavaPreparator;

// This Preparator is just a proof-of-concept. It removes a fraction of the
// training data to make training more "efficient".
public class Preparator extends LJavaPreparator<PreparatorParams, TrainingData, TrainingData> {
  private PreparatorParams pp;
  public Preparator(PreparatorParams pp) {
    this.pp = pp;
  }

  public TrainingData prepare(TrainingData td) {
    int n = (int) (td.r * pp.r);

    Double[][] x = new Double[n][td.c];
    Double[] y = new Double[n];
    for (int i=0; i<n; i++) {
      for (int j=0; j<td.c; j++) {
        x[i][j] = td.x[i][j];
      }
      y[i] = td.y[i];
    }
    return new TrainingData(x, y);
  }
}
