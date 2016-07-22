package org.apache.predictionio.examples.java.recommendations.tutorial3;

import org.apache.predictionio.examples.java.recommendations.tutorial1.TrainingData;
import org.apache.predictionio.examples.java.recommendations.tutorial1.Query;
import org.apache.predictionio.examples.java.recommendations.tutorial1.DataSourceParams;

import org.apache.predictionio.controller.java.LJavaDataSource;
import scala.Tuple2;
import scala.Tuple3;
import java.io.File;
import java.io.FileNotFoundException;
import java.lang.Iterable;
import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.Collections;

public class DataSource extends LJavaDataSource<
  DataSourceParams, Object, TrainingData, Query, Float> {

  final static Logger logger = LoggerFactory.getLogger(DataSource.class);

  DataSourceParams params;

  public DataSource(DataSourceParams params) {
    this.params = params;
  }

  @Override
  public Iterable<Tuple3<Object, TrainingData, Iterable<Tuple2<Query, Float>>>> read() {

    File ratingFile = new File(params.filePath);
    Scanner sc = null;

    try {
      sc = new Scanner(ratingFile);
    } catch (FileNotFoundException e) {
      logger.error("Caught FileNotFoundException " + e.getMessage());
      System.exit(1);
    }

    List<TrainingData.Rating> ratings = new ArrayList<TrainingData.Rating>();

    while (sc.hasNext()) {
      String line = sc.nextLine();
      String[] tokens = line.split("[\t,]");
      try {
        TrainingData.Rating rating = new TrainingData.Rating(
          Integer.parseInt(tokens[0]),
          Integer.parseInt(tokens[1]),
          Float.parseFloat(tokens[2]));
        ratings.add(rating);
      } catch (Exception e) {
        logger.error("Can't parse rating file. Caught Exception: " + e.getMessage());
        System.exit(1);
      }
    }

    int size = ratings.size();
    float trainingPercentage = 0.8f;
    float testPercentage = 1 - trainingPercentage;
    int iterations = 3;

    // cap by original size
    int trainingEndIndex = Math.min(size,
      (int) (ratings.size() * trainingPercentage));
    int testEndIndex = Math.min(size,
      trainingEndIndex + (int) (ratings.size() * testPercentage));
      // trainingEndIndex + 10);

    Random rand = new Random(0); // seed

    List<Tuple3<Object, TrainingData, Iterable<Tuple2<Query, Float>>>> data = new
      ArrayList<Tuple3<Object, TrainingData, Iterable<Tuple2<Query, Float>>>>();

    for (int i = 0; i < iterations; i++) {
      Collections.shuffle(ratings, new Random(rand.nextInt()));

      // create a new ArrayList because subList() returns view and not serialzable
      List<TrainingData.Rating> trainingRatings =
        new ArrayList<TrainingData.Rating>(ratings.subList(0, trainingEndIndex));
      List<TrainingData.Rating> testRatings = ratings.subList(trainingEndIndex, testEndIndex);
      TrainingData td = new TrainingData(trainingRatings);
      List<Tuple2<Query, Float>> qaList = prepareValidation(testRatings);

      data.add(new Tuple3<Object, TrainingData, Iterable<Tuple2<Query, Float>>>(
        null, td, qaList));
    }

    return data;
  }

  private List<Tuple2<Query, Float>> prepareValidation(List<TrainingData.Rating> testRatings) {
    List<Tuple2<Query, Float>> validationList = new ArrayList<Tuple2<Query, Float>>();

    for (TrainingData.Rating r : testRatings) {
      validationList.add(new Tuple2<Query, Float>(
        new Query(r.uid, r.iid),
        r.rating));
    }

    return validationList;
  }

}
