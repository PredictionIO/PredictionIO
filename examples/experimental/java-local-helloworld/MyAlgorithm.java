package org.sample.java.helloworld;

import org.apache.predictionio.controller.java.*;

import java.util.Map;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyAlgorithm extends LJavaAlgorithm<
  EmptyAlgorithmParams, MyTrainingData, MyModel, MyQuery, MyPredictedResult> {

  final static Logger logger = LoggerFactory.getLogger(MyAlgorithm.class);

  @Override
  public MyModel train(MyTrainingData data) {
    Map<String, Double> sumMap = new HashMap<String, Double>();
    Map<String, Integer> countMap = new HashMap<String, Integer>();

    // calculate sum and count for each day
    for (MyTrainingData.DayTemperature temp : data.temperatures) {
      Double sum = sumMap.get(temp.day);
      Integer count = countMap.get(temp.day);
      if (sum == null) {
        sumMap.put(temp.day, temp.temperature);
        countMap.put(temp.day, 1);
      } else {
        sumMap.put(temp.day, sum + temp.temperature);
        countMap.put(temp.day, count + 1);
      }
    }

    // calculate the average
    Map<String, Double> averageMap = new HashMap<String, Double>();
    for (Map.Entry<String, Double> entry : sumMap.entrySet()) {
      String day = entry.getKey();
      Double average = entry.getValue() / countMap.get(day);
      averageMap.put(day, average);
    }

    return new MyModel(averageMap);
  }

  @Override
  public MyPredictedResult predict(MyModel model, MyQuery query) {
    Double temp = model.temperatures.get(query.day);
    return  new MyPredictedResult(temp);
  }
}
