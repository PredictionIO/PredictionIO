package io.prediction.engines.java.olditemrec.algos;

import io.prediction.engines.java.olditemrec.data.PreparedData;
import io.prediction.engines.java.olditemrec.data.Query;
import io.prediction.engines.java.olditemrec.data.Prediction;
import io.prediction.engines.java.olditemrec.data.Model;
import io.prediction.controller.java.LJavaAlgorithm;
import io.prediction.controller.java.JavaParams;
import io.prediction.controller.Params;

import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.common.TasteException;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

/** Mahout Legacy single machine algorithm */
public abstract class AbstractMahoutAlgorithm<AP extends Params>
  extends LJavaAlgorithm<AP, PreparedData, Model, Query, Prediction> {

  MahoutParams params;
  Logger logger;

  public AbstractMahoutAlgorithm(MahoutParams params, Logger logger) {
    this.params = params;
    this.logger = logger;
  }

  abstract public Recommender buildRecommender(PreparedData data) throws TasteException;

  @Override
  public Model train(PreparedData data) {
    Recommender recommender = null;

    try {
      recommender = buildRecommender(data);
    } catch (TasteException e) {
      logger.error("Caught TasteException " + e.getMessage());
    }

    List<Long> uids = new ArrayList<Long>();
    try {
      Iterator<Long> it = data.dataModel.getUserIDs();
      while (it.hasNext()) {
        uids.add(it.next());
      }
    } catch (TasteException e){
      logger.error("Caught TasteException " + e.getMessage());
    }

    //Map<Long, List<RecommendedItem>> itemRecScores = batchTrain(uids, recommender);
    Map<Long, List<RecommendedItem>> itemRecScores = batchTrainMulti(uids, recommender);

    Model m = new Model(itemRecScores);
    logger.info(m.toString());
    return m;

  }

  @Override
  public Prediction predict(Model model, Query query) {
    List<RecommendedItem> items = model.itemRecScores.get((long) query.uid);

    List<Integer> iids = new ArrayList<Integer>();
    List<Float> scores = new ArrayList<Float>();

    if (items != null) {
      int i = 0;
      for (RecommendedItem item: items) {
        logger.info(item.toString());
        iids.add((int) item.getItemID());
        scores.add(item.getValue());

        i++;
        if (i >= query.n) break;
      }
    } else {
      logger.info("null");
    }

    return new Prediction(iids, scores);
  }

  private Map<Long, List<RecommendedItem>> batchTrain(List<Long> uids, Recommender recommender) {
    Map<Long, List<RecommendedItem>> itemRecScores = new HashMap<Long, List<RecommendedItem>>();

    for (Long uid : uids) {
      List<RecommendedItem> items;
      try {
        items = recommender.recommend(uid, params.numRecommendations);
      } catch (TasteException e) {
        items = new ArrayList<RecommendedItem>();
        logger.error("Caught TasteException " + e.getMessage());
      }
      if (items.size() > 0) {
        itemRecScores.put(uid, items);
      }
    }

    return itemRecScores;
  }

  // multi threaded batch training
  private Map<Long, List<RecommendedItem>> batchTrainMulti(
    List<Long> uids, Recommender recommender) {

    int numProcessors = Runtime.getRuntime().availableProcessors();
    ExecutorService executor = Executors.newFixedThreadPool(numProcessors);
    ExecutorCompletionService<Map<Long, List<RecommendedItem>>> pool =
      new ExecutorCompletionService<Map<Long, List<RecommendedItem>>>(executor);

    List<List<Long>> batches = createBatchesByNumber(uids, numProcessors);

    for (List<Long> batch : batches) {
      RecommendTask task = new RecommendTask(batch, recommender);
      pool.submit(task);
    }

    // reduce stage
    Map<Long, List<RecommendedItem>> itemRecScores = new HashMap<Long, List<RecommendedItem>>();
    for (int i = 0; i < batches.size(); i++) {
      try {
        itemRecScores.putAll(pool.take().get());
      } catch (Exception e) {
        logger.error("Caught Exception: " + e.getMessage());
      }
    }
    executor.shutdown();

    return itemRecScores;
  }

  /**
   * Create batches by specifing number of batches
   * @param data List of Data
   * @param num Number of batches
   */
  private List<List<Long>> createBatchesByNumber(List<Long> data, int num) {
    List<List<Long>> batches = new ArrayList<List<Long>>();

    // example cases data.sie()/num:
    // 21/4 = 5 => 5, 5, 5, 6
    // 8/4 = 2 => 2, 2, 2, 2
    // 6/4 = 1 => 1, 1, 1, 3
    // 4/4 = 1 => 1, 1, 1, 1
    // 1/4 = 0 => 1
    // 0/4 = 0 => empty
    int total = data.size();
    int batchSize = 1; // min batchSize is 1;

    if (total > num)
      batchSize = total / num;

    int from = 0;
    int batchNum = 0; // last batch indicator
    int until;
    while (from < total) {
      batchNum += 1;
      if (batchNum == num) // if last batch, just put reamining to the batch
        until = total;
      else
        until = Math.min(total, from + batchSize); // cap by total size
      List<Long> subList = data.subList(from, until);
      logger.info("Create Batch: [" + from + ", " + until + ")");
      batches.add(subList);
      from = until;
    }
    return batches;
  }

/*
  private List<List<Long>> createBatchesBySize(List<Long> data, int batchSize) {
    List<List<Long>> batches = new ArrayList<List<Long>>();

    int i = 0;
    boolean newBatch = true;
    int remain = data.size();
    List<Long> batchData = new ArrayList<Long>();
    int actual = 0;
    for (long d : data) {
      if (newBatch) {
        actual = Math.min(remain, batchSize);
        newBatch = false;
      }
      batchData.add(d);
      remain--;
      i++;
      if (i == actual) { // already fill up one batch
        batches.add(batchData);
        i = 0;
        newBatch = true;
        batchData = new ArrayList<Long>();
      }
    }
    logger.info("batches size: " + batches.size());
    return batches;
  }*/

  private class RecommendTask implements Callable<Map<Long, List<RecommendedItem>>> {

    private final List<Long> uids;
    private final Recommender recommender;

    public RecommendTask(List<Long> uids, Recommender recommender) {
      this.uids = uids;
      this.recommender = recommender;
    }

    @Override
    public Map<Long, List<RecommendedItem>> call() {
      return batchTrain(uids, recommender);
    }
  }

}
