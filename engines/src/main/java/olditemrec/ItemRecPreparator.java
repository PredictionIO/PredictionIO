package io.prediction.engines.java.olditemrec;

import io.prediction.engines.java.olditemrec.data.PreparedData;
import io.prediction.engines.java.olditemrec.data.TrainingData;
import io.prediction.engines.java.olditemrec.data.Rating;
import io.prediction.controller.java.LJavaPreparator;
import io.prediction.controller.java.EmptyParams;
import io.prediction.engines.util.MahoutUtil;

import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.impl.model.GenericDataModel;
import org.apache.mahout.cf.taste.impl.model.file.FileDataModel;

import scala.Tuple4;
import java.util.List;
import java.util.ArrayList;

/** Preparetor for mahout algo */
public class ItemRecPreparator extends LJavaPreparator<
  EmptyParams, TrainingData, PreparedData> {

  @Override
  public PreparedData prepare(TrainingData td) {
    List<Tuple4<Integer, Integer, Float, Long>> ratings = new ArrayList<
      Tuple4<Integer, Integer, Float, Long>>();
    for (Rating r : td.ratings) {
      ratings.add(new Tuple4<Integer, Integer, Float, Long>(r.uid, r.iid, r.rating, r.t));
    }
    DataModel dataModel = MahoutUtil.jBuildDataModel(ratings);
    PreparedData pd = new PreparedData(dataModel);
    return pd;
  }
}
