package io.prediction.algorithms.mahout.itemrec.knnitembased

import org.apache.mahout.cf.taste.common.TasteException
import org.apache.mahout.cf.taste.impl.recommender.GenericItemBasedRecommender
import org.apache.mahout.cf.taste.impl.recommender.AbstractRecommender
import org.apache.mahout.cf.taste.model.DataModel
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity
import org.apache.mahout.cf.taste.recommender.CandidateItemsStrategy
import org.apache.mahout.cf.taste.recommender.MostSimilarItemsCandidateItemsStrategy
import org.apache.mahout.cf.taste.impl.recommender.EstimatedPreferenceCapper

import scala.collection.JavaConversions._

/* Extension to Mahout's GenericItemBasedRecommender
 * with the additional settings: booleanData, neighbourSize, threshold.
 */
class KNNItemBasedRecommender(dataModel: DataModel,
  similarity: ItemSimilarity,
  candidateItemsStrategy: CandidateItemsStrategy,
  mostSimilarItemsCandidateItemsStrategy: MostSimilarItemsCandidateItemsStrategy,
  booleanData: Boolean,
  neighbourSize: Int,
  threshold: Double) extends GenericItemBasedRecommender(dataModel, similarity, candidateItemsStrategy,
  mostSimilarItemsCandidateItemsStrategy) {

  val capper: Option[EstimatedPreferenceCapper] = if (getDataModel().getMinPreference().isNaN ||
    getDataModel().getMaxPreference().isNaN)
    None
  else
    Some(new EstimatedPreferenceCapper(getDataModel()))

  def this(dataModel: DataModel, similarity: ItemSimilarity, booleanData: Boolean, neighbourSize: Int, threshold: Double) =
    this(dataModel, similarity, AbstractRecommender.getDefaultCandidateItemsStrategy(),
      GenericItemBasedRecommender.getDefaultMostSimilarItemsCandidateItemsStrategy(), booleanData, neighbourSize, threshold)

  @throws(classOf[TasteException])
  override def doEstimatePreference(userID: Long, preferencesFromUser: PreferenceArray, itemID: Long): Float = {
    val neighbourRatedIds = preferencesFromUser.getIDs()
      .zipWithIndex
      .map { case (id, index) => (id, similarity.itemSimilarity(itemID, id), index) } // (id, simiarity, index)
      .filter { case (id, sim, index) => (!sim.isNaN()) && (sim >= threshold) }
      .sortBy(_._2)(Ordering[Double].reverse)
      .take(neighbourSize)

    val estimatedPreference: Float = if (booleanData) {
      val totalSimilarity = neighbourRatedIds.foldLeft[Double](0) { (acc, x) =>
        val (id, sim, index) = x
        acc + sim
      }
      totalSimilarity.toFloat
    } else {
      val (totalPreference, totalSimilarity) = neighbourRatedIds.foldLeft[(Double, Double)]((0, 0)) { (acc, x) =>
        val (accPreference, accSimilarity) = acc
        val (id, sim, index) = x

        val totalPreference = accPreference + (sim * preferencesFromUser.getValue(index))
        val totalSimilarity = accSimilarity + sim
        (totalPreference, totalSimilarity)
      }
      // if there is only 1 similar item, the estimate preference will be same as the preferewnce of that item
      // regardless of similarity. so don't count it and return NaN instead.
      if (neighbourRatedIds.size <= 1) {
        Float.NaN
      } else {
        val estimate = (totalPreference / totalSimilarity).toFloat
        val cappedEstimate = capper.map(c => c.capEstimate(estimate)).getOrElse(estimate)
        cappedEstimate
      }
    }
    estimatedPreference
  }

  override def toString() = {
    "KNNItemBasedRecommender"
  }
}
