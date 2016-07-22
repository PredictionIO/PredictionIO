package org.sample.java.helloworld;

import org.apache.predictionio.controller.java.*;

import java.util.List;
import java.util.ArrayList;
import java.io.FileReader;
import java.io.BufferedReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyDataSource extends LJavaDataSource<
  EmptyDataSourceParams, EmptyDataParams, MyTrainingData, MyQuery, EmptyActualResult> {

  final static Logger logger = LoggerFactory.getLogger(MyDataSource.class);

  @Override
  public MyTrainingData readTraining() {
    List<MyTrainingData.DayTemperature> temperatures =
      new ArrayList<MyTrainingData.DayTemperature>();

    try {
      BufferedReader reader = new BufferedReader(new FileReader("../data/helloworld/data.csv"));
      String line;
      while ((line = reader.readLine()) != null) {
        String[] tokens = line.split(",");
        temperatures.add(
          new MyTrainingData.DayTemperature(tokens[0], Double.parseDouble(tokens[1])));
      }
      reader.close();
    } catch (Exception e) {
      logger.error("Caught Exception: " + e.getMessage());
      System.exit(1);
    }

    return new MyTrainingData(temperatures);
  }
}
