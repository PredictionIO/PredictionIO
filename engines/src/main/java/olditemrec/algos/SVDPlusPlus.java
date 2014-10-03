package io.prediction.engines.java.olditemrec.algos;

import io.prediction.engines.java.olditemrec.data.PreparedData;

import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.impl.recommender.svd.SVDRecommender;
import org.apache.mahout.cf.taste.impl.recommender.svd.SVDPlusPlusFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorizer;
import org.apache.mahout.cf.taste.common.TasteException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SVDPlusPlus
  extends AbstractMahoutAlgorithm<SVDPlusPlusParams> {

  final static Logger logger = LoggerFactory.getLogger(SVDPlusPlus.class);

  SVDPlusPlusParams params;

  public SVDPlusPlus(SVDPlusPlusParams params) {
    super(params, logger);
    this.params = params;
  }

  @Override
  public Recommender buildRecommender(PreparedData data) throws TasteException {
    int numFeatures = params.numFeatures();
    double learningRate = params.learningRate();
    double preventOverfitting = params.preventOverfitting();
    double randomNoise = params.randomNoise();
    int numIterations = params.numIterations();
    double learningRateDecay = params.learningRateDecay();

    Recommender recommender = null;
    // TODO: handle Exception
    try {
    Factorizer factorizer = new SVDPlusPlusFactorizer(
      data.dataModel, numFeatures, learningRate, preventOverfitting,
      randomNoise, numIterations, learningRateDecay);

    // TODO: support other candidate item strategy
      recommender = new SVDRecommender(data.dataModel, factorizer);
    } catch (TasteException e) {
      logger.error("Caught TasteException " + e.getMessage());
    }
    return recommender;
  }
}
