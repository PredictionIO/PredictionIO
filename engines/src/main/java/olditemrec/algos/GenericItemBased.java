package io.prediction.engines.java.olditemrec.algos;

import io.prediction.engines.java.olditemrec.data.PreparedData;

import org.apache.mahout.cf.taste.similarity.ItemSimilarity;
import org.apache.mahout.cf.taste.impl.recommender.GenericItemBasedRecommender;
import org.apache.mahout.cf.taste.impl.similarity.CityBlockSimilarity;
import org.apache.mahout.cf.taste.impl.similarity.EuclideanDistanceSimilarity;
import org.apache.mahout.cf.taste.impl.similarity.LogLikelihoodSimilarity;
import org.apache.mahout.cf.taste.impl.similarity.PearsonCorrelationSimilarity;
import org.apache.mahout.cf.taste.impl.similarity.TanimotoCoefficientSimilarity;
import org.apache.mahout.cf.taste.impl.similarity.UncenteredCosineSimilarity;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.common.Weighting;
import org.apache.mahout.cf.taste.common.TasteException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// use TrainigData as CD for now
public class GenericItemBased
  extends AbstractMahoutAlgorithm<GenericItemBasedParams> {

  final static Logger logger = LoggerFactory.getLogger(GenericItemBased.class);

  final static String CITY_BLOCK = "CityBlockSimilarity";
  final static String EUCLIDEAN_DISTANCE = "EuclideanDistanceSimilarity";
  final static String LOG_LIKELIHOOD = "LogLikelihoodSimilarity";
  final static String PEARSON_CORRELATION = "PearsonCorrelationSimilarity";
  final static String TANIMOTO_COEFFICIENT = "TanimotoCoefficientSimilarity";
  final static String UNCENTERED_COSINE = "UncenteredCosineSimilarity";

  GenericItemBasedParams params;

  public GenericItemBased(GenericItemBasedParams params) {
    super(params, logger);
    this.params = params;
  }

  @Override
  public Recommender buildRecommender(PreparedData data) throws TasteException {

    String itemSimilarity = params.itemSimilarity();
    boolean weighted = params.weighted();

    Weighting weightedParam;

    if (weighted)
      weightedParam = Weighting.WEIGHTED;
    else
      weightedParam = Weighting.UNWEIGHTED;

    ItemSimilarity similarity;
    switch (itemSimilarity) {
      case CITY_BLOCK:
        similarity = new CityBlockSimilarity(data.dataModel);
        break;
      case EUCLIDEAN_DISTANCE:
        similarity = new EuclideanDistanceSimilarity(data.dataModel, weightedParam);
        break;
      case LOG_LIKELIHOOD:
        similarity = new LogLikelihoodSimilarity(data.dataModel);
        break;
      case PEARSON_CORRELATION:
        similarity = new PearsonCorrelationSimilarity(data.dataModel, weightedParam);
        break;
      case TANIMOTO_COEFFICIENT:
        similarity = new TanimotoCoefficientSimilarity(data.dataModel);
        break;
      case UNCENTERED_COSINE:
        similarity = new UncenteredCosineSimilarity(data.dataModel, weightedParam);
        break;
      default:
        logger.error("Invalid itemSimilarity: " + itemSimilarity +
          ". LogLikelihoodSimilarity is used.");
        similarity = new LogLikelihoodSimilarity(data.dataModel);
        break;
    }

    Recommender recommender = new GenericItemBasedRecommender(
      data.dataModel,
      similarity
      // TODO: support other candidate item strategy
      //AbstractRecommender.getDefaultCandidateItemsStrategy(),
      //GenericItemBasedRecommender.getDefaultMostSimilarItemsCandidateItemsStrategy()
      );

    return recommender;
  }
}
