package myengine;

import io.prediction.controller.java.LJavaDataSource;

import myengine.MyParams.DataSourceParams;
import myengine.MyParams.DataParams;
import myengine.MyData.TrainingData;
import myengine.MyData.Query;
import myengine.MyData.Actual;

import scala.Tuple2;
import scala.Tuple3;

import java.lang.Iterable;
import java.util.ArrayList;

public class DataSource extends LJavaDataSource<
  DataSourceParams, DataParams, TrainingData, Query, Actual> {

  public DataSource(DataSourceParams params) {

  }

  @Override
  public Iterable<Tuple3<DataParams, TrainingData, Iterable<Tuple2<Query, Actual>>>> read() {
    return new ArrayList<Tuple3<DataParams, TrainingData, Iterable<Tuple2<Query, Actual>>>>();
  }

}
