package io.prediction.engines.java.olditemrec;

import io.prediction.controller.java.LJavaDataSource;
import io.prediction.controller.java.EmptyParams;
import io.prediction.engines.java.olditemrec.data.Query;
import io.prediction.engines.java.olditemrec.data.Actual;
import io.prediction.engines.java.olditemrec.data.TrainingData;
import io.prediction.engines.java.olditemrec.data.Rating;

import scala.Tuple2;
import scala.Tuple3;
import scala.Tuple4;
import java.io.File;
import java.lang.Iterable;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.Random;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.lang.Math;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This Data Source reads a tab or comma delimited rating file:
 *   uid, iid, rating(, timestamp)
 * uid: int
 * iid: int
 * rating: float
 * timestamp: long. it's optional
 */
// use EmptyParas as DP
public class ItemRecDataSource extends LJavaDataSource<
  DataSourceParams, EmptyParams, TrainingData, Query, Actual> {

  final static Logger logger = LoggerFactory.getLogger(ItemRecDataSource.class);

  DataSourceParams dsp;

  public ItemRecDataSource(DataSourceParams dsp) {
    this.dsp = dsp;
  }

  @Override
  public Iterable<Tuple3<EmptyParams, TrainingData, Iterable<Tuple2<Query, Actual>>>> read() {
    File ratingFile = new File(dsp.filePath);
    Scanner sc = null;

    try {
      sc = new Scanner(ratingFile);
    } catch (FileNotFoundException e) {
      logger.error("Caught FileNotFoundException " + e.getMessage());
      System.exit(1);
    }

    List<Rating> ratings = new ArrayList<Rating>();

    while (sc.hasNext()) {
      String line = sc.nextLine();
      String[] tokens = line.split("[\t,]");
      try {
        Long timestamp = 0L;
        if (tokens.length >= 4) {
          timestamp = Long.parseLong(tokens[3]);
        }
        Rating rating = new Rating(
          Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]),
          Float.parseFloat(tokens[2]), timestamp);
        ratings.add(rating);
      } catch (Exception e) {
        logger.error("Can't parse rating file. Caught Exception: " + e.getMessage());
        System.exit(1);
      }
    }

    int size = ratings.size();
    // cap by original size
    int trainingEndIndex = Math.min(size,
      (int) (ratings.size() * dsp.trainingPercentage));
    int testEndIndex = Math.min( size,
      trainingEndIndex + (int) (ratings.size() * dsp.testPercentage));

    Random rand = new Random(dsp.seed);

    List<Tuple3<EmptyParams, TrainingData, Iterable<Tuple2<Query, Actual>>>> data = new
      ArrayList<Tuple3<EmptyParams, TrainingData, Iterable<Tuple2<Query, Actual>>>>();

    for (int i = 0; i < dsp.iterations; i++) {
      Collections.shuffle(ratings, new Random(rand.nextInt()));

      // create a new ArrayList because subList() returns view and not serialzable
      List<Rating> trainingRatings = new ArrayList<Rating>(ratings.subList(0, trainingEndIndex));
      List<Rating> testRatings = ratings.subList(trainingEndIndex, testEndIndex);
      TrainingData td = prepareTraining(trainingRatings);
      List<Tuple2<Query, Actual>> qaList = prepareValidation(testRatings);

      data.add(new Tuple3<EmptyParams, TrainingData, Iterable<Tuple2<Query, Actual>>>(
        new EmptyParams(), td, qaList));
    }

    return data;
  }

  private TrainingData prepareTraining(List<Rating> trainingRatings) {
    return new TrainingData(trainingRatings);
  }

  private List<Tuple2<Query, Actual>> prepareValidation(List<Rating> testRatings) {

    Map<Integer, Set<Integer>> relevantItems = new HashMap<Integer, Set<Integer>>();
    if (testRatings.size() > 0) {
      for (Rating rating: testRatings) {
        if (rating.rating >= dsp.goal) {
          Set<Integer> items = relevantItems.get(rating.uid);
          if (items == null) {
            items = new HashSet<Integer>();
            items.add(rating.iid);
            relevantItems.put(rating.uid, items);
          } else {
            items.add(rating.iid);
          }
        }
      }
    }

    List<Tuple2<Query, Actual>> qaList = new ArrayList<Tuple2<Query, Actual>>();
    for (Map.Entry<Integer, Set<Integer>> entry : relevantItems.entrySet()) {
      int key = entry.getKey();
      Set<Integer> value = entry.getValue();
      qaList.add(new Tuple2<Query, Actual>(new Query(key, dsp.k), new Actual(value)));
    }

    return qaList;
  }

}
