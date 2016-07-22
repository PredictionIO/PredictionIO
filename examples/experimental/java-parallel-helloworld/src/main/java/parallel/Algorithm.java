package org.apache.predictionio.examples.java.parallel;

import org.apache.predictionio.controller.java.EmptyParams;
import org.apache.predictionio.controller.java.PJavaAlgorithm;

import java.io.Serializable;
import java.util.List;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.api.java.function.PairFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Tuple2;

public class Algorithm extends PJavaAlgorithm<
  EmptyParams, JavaPairRDD<String, Float>, Model, Query, Float> {

  final static Logger logger = LoggerFactory.getLogger(Algorithm.class);

  public static class ReadingAndCount implements Serializable {
    public float reading;
    public int count;

    public ReadingAndCount(float reading, int count) {
      this.reading = reading;
      this.count = count;
    }

    public ReadingAndCount(float reading) {
      this(reading, 1);
    }

    @Override
    public String toString() {
      return "(reading = " + reading + ", count = " + count + ")";
    }
  }

  @Override
  public Model train(JavaPairRDD<String, Float> data) {
    // take averages just like the local helloworld program
    JavaPairRDD<String, Float> averages = data.mapValues(
      new Function<Float, ReadingAndCount>() {
        @Override
        public ReadingAndCount call(Float reading) {
          return new ReadingAndCount(reading);
        }
      }).reduceByKey(
      new Function2<ReadingAndCount, ReadingAndCount, ReadingAndCount>() {
        @Override
        public ReadingAndCount call(ReadingAndCount rac1, ReadingAndCount rac2) {
          return new ReadingAndCount(rac1.reading + rac2.reading, rac1.count + rac2.count);
        }
      }).mapValues(
      new Function<ReadingAndCount, Float>() {
        @Override
        public Float call(ReadingAndCount rac) {
          return rac.reading / rac.count;
        }
      });
    return new Model(averages);
  }

  @Override
  public JavaPairRDD<Object, Float> batchPredict(Model model,
      JavaPairRDD<Object, Query> indexedQueries) {
    return model.temperatures.join(indexedQueries.mapToPair(
        new PairFunction<Tuple2<Object, Query>, String, Object>() {
          @Override   // reverse the query tuples, then join
          public Tuple2 call(Tuple2<Object, Query> tuple) {
            return new Tuple2(tuple._2.day, tuple._1);
          }
        })).mapToPair(
        new PairFunction<Tuple2<String, Tuple2<Float, Object>>, Object, Float>() {
          @Override   // map result back to predictions, dropping the day
          public Tuple2 call(Tuple2<String, Tuple2<Float, Object>> tuple) {
            return new Tuple2(tuple._2._2, tuple._2._1);
          }
        });
  }

  @Override
  public Float predict(Model model, Query query) {
    final String day = query.day;
    List<Float> reading = model.temperatures.lookup(day);
    if (reading.size() == 0) {
      return -10000f; // JSON does not support NaN
    }
    return reading.get(0);
  }
}
