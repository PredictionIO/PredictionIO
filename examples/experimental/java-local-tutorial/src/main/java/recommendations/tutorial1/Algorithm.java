package org.apache.predictionio.examples.java.recommendations.tutorial1;

import org.apache.predictionio.controller.java.LJavaAlgorithm;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.OpenMapRealVector;
import scala.Tuple2;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Queue;
import java.util.PriorityQueue;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Algorithm extends
  LJavaAlgorithm<AlgoParams, TrainingData, Model, Query, Float> {

  final static Logger logger = LoggerFactory.getLogger(Algorithm.class);

  AlgoParams params;

  public Algorithm(AlgoParams params) {
    this.params = params;
  }

  @Override
  public Model train(TrainingData data) {
    // pre-process
    Map<Integer, Map<Integer, Float>> itemMap = new HashMap<Integer, Map<Integer, Float>>();
    Map<Integer, Integer> userIndexMap = new HashMap<Integer, Integer>();
    Map<Integer, Integer> itemIndexMap = new HashMap<Integer, Integer>();

    int itemIndex = 0;
    int userIndex = 0;
    for (TrainingData.Rating r : data.ratings) {
      Map<Integer, Float> userRating = itemMap.get(r.iid);
      if (userRating == null) {
        // new item
        userRating = new HashMap<Integer, Float>();
        itemMap.put(r.iid, userRating);
        itemIndexMap.put(r.iid, itemIndex);
        itemIndex += 1; // increment item index for next item
      }
      userRating.put(r.uid, r.rating);

      // update user index
      Integer u = userIndexMap.get(r.uid);
      if (u == null) {
        // new user
        userIndexMap.put(r.uid, userIndex);
        userIndex += 1;
      }
    }

    int numOfItems = itemIndexMap.size();
    int numOfUsers = userIndexMap.size();

    Map<Integer, RealVector> itemVectors = new HashMap<Integer, RealVector>();
    Map<Integer, RealVector> userHistory = new HashMap<Integer, RealVector>();

    for (Map.Entry<Integer, Map<Integer, Float>> entry : itemMap.entrySet()) {
      Integer itemID = entry.getKey();
      Integer iindex = itemIndexMap.get(itemID);
      Map<Integer, Float> userRatingMap = entry.getValue();
      RealVector item = new ArrayRealVector(numOfUsers); // dimension is numOfUsers
      for (Map.Entry<Integer, Float> r : userRatingMap.entrySet()) {
        Integer userID = r.getKey();
        Float rating = r.getValue();
        Integer uindex = userIndexMap.get(userID);
        item.setEntry(uindex, rating);
        // update user History
        RealVector user = userHistory.get(userID);
        if (user == null) {
          user = new OpenMapRealVector(numOfItems);
          userHistory.put(userID, user);
        }
        user.setEntry(iindex, rating);
      }
      itemVectors.put(itemID, item);
    }

    // calculate sim

    Map<Integer, RealVector> itemSimilarity = new HashMap<Integer, RealVector>();
    List<Integer> item1List = new ArrayList<Integer>(itemIndexMap.keySet());
    List<Integer> item2List = new ArrayList<Integer>(item1List);

    int numSimilarItems = 100;
    Comparator<IndexAndScore> comparator = new IndexAndScoreComparator();
    Map<Integer, Queue<IndexAndScore>> topItemSimilarity =
      new HashMap<Integer, Queue<IndexAndScore>>();

    for (Integer itemID1 : item1List) {
      item2List.remove(0);
      Integer index1 = itemIndexMap.get(itemID1);
      for (Integer itemID2: item2List) {
        RealVector vector1 = itemVectors.get(itemID1);
        RealVector vector2 = itemVectors.get(itemID2);
        double score = vector1.cosine(vector2);
        if (score > params.threshold) {
          Integer index2 = itemIndexMap.get(itemID2);
          setTopItemSimilarity(topItemSimilarity, itemID1, index2, score, numSimilarItems,
            comparator);
          setTopItemSimilarity(topItemSimilarity, itemID2, index1, score, numSimilarItems,
            comparator);
        }
      }
    }

    for (Map.Entry<Integer, Queue<IndexAndScore>> entry : topItemSimilarity.entrySet()) {
      Iterator<IndexAndScore> it = entry.getValue().iterator();
      RealVector vector = new OpenMapRealVector(numOfItems);
      while (it.hasNext()) {
        IndexAndScore d = it.next();
        vector.setEntry(d.index, d.score);
      }
      itemSimilarity.put(entry.getKey(), vector);
    }

    return new Model(itemSimilarity, userHistory);
  }

  private class IndexAndScore {
    int index;
    double score;
    public IndexAndScore(int index, double score) {
      this.index = index;
      this.score = score;
    }
  }

  private class IndexAndScoreComparator implements Comparator<IndexAndScore> {
    @Override
    public int compare(IndexAndScore o1, IndexAndScore o2) {
      int r = 0;
      if (o1.score < o2.score)
        r = -1;
      else if (o1.score > o2.score)
        r = 1;
      return r;
    }
  }

  private void setTopItemSimilarity(Map<Integer, Queue<IndexAndScore>> topItemSimilarity,
    Integer itemID1, Integer index2, double score, int capacity,
    Comparator<IndexAndScore> comparator) {
    Queue<IndexAndScore> queue = topItemSimilarity.get(itemID1);
    if (queue == null) {
      queue = new PriorityQueue<IndexAndScore>(capacity, comparator);
      topItemSimilarity.put(itemID1, queue);
    }
    IndexAndScore entry = new IndexAndScore(index2, score);
    if (queue.size() < capacity)
      queue.add(entry);
    else if (comparator.compare(queue.peek(), entry) < 0) {
      queue.poll();
      queue.add(entry);
    }
  }

  @Override
  public Float predict(Model model, Query query) {
    RealVector itemVector = model.itemSimilarity.get(query.iid);
    RealVector userVector = model.userHistory.get(query.uid);
    if (itemVector == null) {
      // cold start item, can't be handled by this algo, return hard code value.
      return Float.NaN;
    } else if (userVector == null) {
      // new user, can't be handled by this algo, return hard code value.
      return Float.NaN;
    } else {
      //logger.info("(" + query.uid + "," + query.iid + ")");
      //logger.info(itemVector.toString());
      //logger.info(userVector.toString());
      double accum = 0.0;
      double accumSim = 0.0;
      for (int i = 0; i < itemVector.getDimension(); i++) {
        double weight = itemVector.getEntry(i);
        double rating = userVector.getEntry(i);
        if ((weight != 0) && (rating != 0)) {
          accum += weight * rating;
          accumSim += Math.abs(weight);
        }
      }

      if (accumSim == 0.0) {
        return Float.NaN;
      } else {
        return (float) (accum / accumSim);
      }
    }
  }

}
