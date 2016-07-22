package org.apache.predictionio.examples.java.recommendations.tutorial4;

import java.util.Arrays;
import org.apache.predictionio.controller.java.LJavaDataSource;
import org.apache.predictionio.controller.java.EmptyParams;
import scala.Tuple2;
import scala.Tuple3;
import java.io.File;
import java.io.FileNotFoundException;
import java.lang.Iterable;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.io.FileReader;
import java.io.BufferedReader;


public class DataSource extends LJavaDataSource<
  DataSourceParams, EmptyParams, TrainingData, Query, Object> {

  final static Logger logger = LoggerFactory.getLogger(DataSource.class);

  DataSourceParams params;

  public static class FakeData {
    // User -1 is a action movie lover. He should have high ratings for Item -2 ("Cold Action
    // Movie"). Notice that Item -2 is new, hence have no rating. Feature-based algorithm should be
    // able to return a high rating using user profile.
    public static final List<String> itemData = Arrays.asList(
        "-1|Action Movie (2046)|01-Jan-2046||http://i.dont.exist/|0|1|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0",
        "-2|Cold Action Movie II(2047)|01-Jan-2047||http://i.dont.exist/|0|1|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0",
        "-3|Documentary (1997)|01-July-1997||http://no.pun.intended/|0|0|0|0|0|0|0|1|0|0|0|0|0|0|0|0|0|0|0");
    public static final List<String> userData = Arrays.asList(
        "-1|30|M|action_lover|94087",
        "-2|30|M|documentary_lover|94087",
        "-3|30|M|cold|94087");
    public static final List<String> ratingData = Arrays.asList(
        "-1,-1,5,881250949",
        "-2,-3,5,881250949",
        "-2,1,5,881250949");
  }

  public DataSource(DataSourceParams params) {
    this.params = params;
  }

  public List<String[]> readFile(String filepath, String delimiter) {
    return readFile(filepath, delimiter, new ArrayList<String>());
  }

  public List<String[]> readFile(String filepath, String delimiter, List<String> fakeData) {
    List<String[]> tokensList = new ArrayList<String[]>();
    try {
      List<String> lines = new ArrayList<String>();
      lines.addAll(fakeData);

      BufferedReader in = new BufferedReader(new FileReader(filepath));

      while (in.ready()) {
        String s = in.readLine();
        lines.add(s);
      }
      in.close();

      for (String line: lines) {
        String[] tokens = line.split(delimiter);
        tokensList.add(tokens);
      }

    } catch (FileNotFoundException e) {
      logger.error("Caught FileNotFoundException " + e.getMessage());
      System.exit(1);
    } catch (Exception e) {
      logger.error("Can't parse file. Caught Exception: " + e.getMessage()
          + "Trace: " + Arrays.toString(e.getStackTrace()));
      System.exit(1);
    }

    return tokensList;
  }

  public List<TrainingData.Rating> getRatings() {
    List<TrainingData.Rating> ratings = new ArrayList<TrainingData.Rating>();

    List<String[]> tokensList = readFile(params.dir + "u.data", "[\t,]",
        (params.addFakeData) ? FakeData.ratingData : new ArrayList<String>());

    for (String[] tokens: tokensList) {
      TrainingData.Rating rating = new TrainingData.Rating(
          Integer.parseInt(tokens[0]),
          Integer.parseInt(tokens[1]),
          Float.parseFloat(tokens[2]));
      ratings.add(rating);
    }

    return ratings;
  }

  public List<String> getGenres() {
    List<String> genres = new ArrayList<String>();
    for (String[] tokens: readFile(params.dir + "u.genre", "[\t,]")) {
      if (!tokens[0].equals("")) {
        genres.add(tokens[0]);
      }
    }
    return genres;
  }

  public Map<Integer, String[]> getItemInfo() {
    List<String[]> tokensList = readFile(params.dir + "u.item", "[\\|]",
        (params.addFakeData) ? FakeData.itemData : new ArrayList<String>());


    Map<Integer, String[]> itemInfo = new HashMap <> ();
    for (String[] tokens : tokensList) {
      itemInfo.put(Integer.parseInt(tokens[0]), tokens);
    }

    return itemInfo;
  }

  public Map<Integer, String[]> getUserInfo() {
    List<String[]> tokensList = readFile(params.dir + "u.user", "[\\|]",
        (params.addFakeData) ? FakeData.userData : new ArrayList<String>());

    Map<Integer, String[]> userInfo = new HashMap <> ();
    for (String[] tokens : tokensList) {
      userInfo.put(Integer.parseInt(tokens[0]), tokens);
    }
    return userInfo;
  }

  @Override
  public Iterable<Tuple3<EmptyParams, TrainingData, Iterable<Tuple2<Query, Object>>>> read() {
    List<Tuple3<EmptyParams, TrainingData, Iterable<Tuple2<Query, Object>>>> data =
      new ArrayList<Tuple3<EmptyParams, TrainingData, Iterable<Tuple2<Query, Object>>>>();

    data.add(new Tuple3<EmptyParams, TrainingData, Iterable<Tuple2<Query, Object>>>(
      new EmptyParams(),
      new TrainingData(getRatings(), getGenres(), getItemInfo(), getUserInfo()),
      new ArrayList<Tuple2<Query, Object>>()
    ));

    return data;
  }
}
