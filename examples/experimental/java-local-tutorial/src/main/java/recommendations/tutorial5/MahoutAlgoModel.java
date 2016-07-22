package org.apache.predictionio.examples.java.recommendations.tutorial5;

import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.impl.recommender.GenericItemBasedRecommender;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;
import org.apache.mahout.cf.taste.impl.similarity.LogLikelihoodSimilarity;
import org.apache.mahout.cf.taste.impl.similarity.TanimotoCoefficientSimilarity;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MahoutAlgoModel implements Serializable, KryoSerializable {

  private DataModel dataModel;
  private MahoutAlgoParams params;
  private transient Recommender recommender; // declare "transient" because it's not serializable.

  final static Logger logger = LoggerFactory.getLogger(MahoutAlgoModel.class);

  public MahoutAlgoModel(DataModel dataModel, MahoutAlgoParams params) {
    this.dataModel = dataModel;
    this.params = params;
    this.recommender = buildRecommender(this.dataModel, this.params);
  }

  public Recommender getRecommender() {
    return this.recommender;
  }

  private Recommender buildRecommender(DataModel dataModel, MahoutAlgoParams params) {
    ItemSimilarity similarity;
    switch (params.itemSimilarity) {
      case MahoutAlgoParams.LOG_LIKELIHOOD:
        similarity = new LogLikelihoodSimilarity(dataModel);
        break;
      case MahoutAlgoParams.TANIMOTO_COEFFICIENT:
        similarity = new TanimotoCoefficientSimilarity(dataModel);
        break;
      default:
        logger.error("Invalid itemSimilarity: " + params.itemSimilarity +
          ". LogLikelihoodSimilarity is used.");
        similarity = new LogLikelihoodSimilarity(dataModel);
        break;
    }
    return new GenericItemBasedRecommender(
      dataModel,
      similarity
    );
  }

  // KryoSerializable interface
  public void write (Kryo kryo, Output output) {
    kryo.writeClassAndObject(output, this.dataModel);
    kryo.writeClassAndObject(output, this.params);
  }

  // KryoSerializable interface
  public void read (Kryo kryo, Input input) {
    this.dataModel = (DataModel) kryo.readClassAndObject(input);
    this.params = (MahoutAlgoParams) kryo.readClassAndObject(input);
    this.recommender = buildRecommender(this.dataModel, this.params); // recover the recommender
  }

  @Override
  public String toString() {
    return "Mahout Recommender";
  }
}
