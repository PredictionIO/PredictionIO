package org.apache.predictionio.examples.java.recommendations.tutorial5;

import org.apache.predictionio.controller.java.LJavaAlgorithm;
import org.apache.predictionio.controller.java.EmptyParams;
import org.apache.predictionio.examples.java.recommendations.tutorial1.TrainingData;
import org.apache.predictionio.examples.java.recommendations.tutorial1.Query;
import org.apache.predictionio.engines.util.MahoutUtil;

import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.common.TasteException;
import scala.Tuple4;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Simple Mahout ItemBased Algorithm integration for demonstration purpose */
public class MahoutAlgorithm extends
  LJavaAlgorithm<EmptyParams, TrainingData, MahoutAlgoModel, Query, Float> {

  final static Logger logger = LoggerFactory.getLogger(MahoutAlgorithm.class);

  MahoutAlgoParams params;

  public MahoutAlgorithm(MahoutAlgoParams params) {
    this.params = params;
  }

  @Override
  public MahoutAlgoModel train(TrainingData data) {
    List<Tuple4<Integer, Integer, Float, Long>> ratings = new ArrayList<
      Tuple4<Integer, Integer, Float, Long>>();
    for (TrainingData.Rating r : data.ratings) {
      // no timestamp
      ratings.add(new Tuple4<Integer, Integer, Float, Long>(r.uid, r.iid, r.rating, 0L));
    }
    DataModel dataModel = MahoutUtil.jBuildDataModel(ratings);
    return new MahoutAlgoModel(dataModel, params);
  }

  @Override
  public Float predict(MahoutAlgoModel model, Query query) {
    float predicted;
    try {
      predicted = model.getRecommender().estimatePreference((long) query.uid, (long) query.iid);
    } catch (TasteException e) {
      predicted = Float.NaN;
    }
    return predicted;
  }

}
