package org.apache.predictionio.examples.java.parallel;

import java.io.Serializable;
import java.lang.StringBuilder;
import java.util.List;

import org.apache.spark.api.java.JavaPairRDD;
import scala.Tuple2;

public class Model implements Serializable {
  private static int LIST_THRESHOLD = 20;

  public JavaPairRDD<String, Float> temperatures;

  public Model(JavaPairRDD<String, Float> temperatures) {
    this.temperatures = temperatures;
  }

  @Override
  public String toString() {
    boolean longList = temperatures.count() > LIST_THRESHOLD ? true : false;
    List<Tuple2<String, Float>> readings =
      temperatures.take(longList ? LIST_THRESHOLD : (int) temperatures.count());
    StringBuilder builder = new StringBuilder();
    builder.append("(");
    boolean first = true;
    for (Tuple2<String, Float> reading : readings) {
      if (!first) {
        builder.append(", ");
      } else {
        first = false;
      }
      builder.append(reading);
    }
    if (longList) {
      builder.append(", ...");
    }
    builder.append(")");
    return builder.toString();
  }
}
