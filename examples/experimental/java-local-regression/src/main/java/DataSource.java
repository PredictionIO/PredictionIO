package org.apache.predictionio.examples.java.regression;

import org.apache.predictionio.controller.java.LJavaDataSource;

import scala.Tuple2;
import scala.Tuple3;

import java.io.IOException;
import java.lang.Iterable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class DataSource
  extends LJavaDataSource<DataSourceParams, Integer, TrainingData, Double[], Double> {

  private static final Pattern SPACE = Pattern.compile(" ");

  public final DataSourceParams dsp;

  public DataSource(DataSourceParams dsp) {
    this.dsp = dsp;
  }

  public List<Tuple3<Integer, TrainingData, Iterable<Tuple2<Double[], Double>>>> read() {
 
    List<String> lines;
    try {
      lines = Files.readAllLines(Paths.get(dsp.filepath), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      System.out.println("Cannot read file");
      lines = new ArrayList<String>();
    }

    int n = lines.size();

    int featureCount = SPACE.split(lines.get(0)).length - 1;

    Double[][] x = new Double[n][featureCount]; 
    Double[] y = new Double[n];

    for (int i = 0; i < n; i++) {
      String[] line = SPACE.split(lines.get(i), 2);
      y[i] = Double.parseDouble(line[0]);

      String[] featureStrs = SPACE.split(line[1]);

      for (int j = 0; j < featureCount; j++) {
        x[i][j] = Double.parseDouble(featureStrs[j]);
      }
    }

    TrainingData td = new TrainingData(x, y);

    List<Tuple2<Double[], Double>> faList = new ArrayList<>();

    for (int i = 0; i < 10; i++) {
      faList.add(new Tuple2<Double[], Double>(x[i], y[i]));
    }

    List<Tuple3<Integer, TrainingData, Iterable<Tuple2<Double[], Double>>>> results = 
      new ArrayList <> ();

    results.add(new Tuple3<Integer, TrainingData, Iterable<Tuple2<Double[], Double>>>(
      new Integer(0), td, faList));

    return results;
  }
}
