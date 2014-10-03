/** Copyright 2014 TappingStone, Inc.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package io.prediction.engines.base.mahout

import org.apache.mahout.cf.taste.common.TasteException
import org.apache.mahout.cf.taste.impl.recommender.GenericItemBasedRecommender
import org.apache.mahout.cf.taste.impl.recommender.AbstractRecommender
import org.apache.mahout.cf.taste.model.DataModel
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity
import org.apache.mahout.cf.taste.recommender.CandidateItemsStrategy
import org.apache.mahout.cf.taste.recommender.MostSimilarItemsCandidateItemsStrategy
import org.apache.mahout.cf.taste.impl.recommender.EstimatedPreferenceCapper

import scala.collection.mutable.PriorityQueue
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

  def this(dataModel: DataModel, similarity: ItemSimilarity,
    candidateItemsStrategy: CandidateItemsStrategy,
    booleanData: Boolean, neighbourSize: Int, threshold: Double) =
    this(dataModel, similarity, candidateItemsStrategy,
      GenericItemBasedRecommender.getDefaultMostSimilarItemsCandidateItemsStrategy(),
      booleanData, neighbourSize, threshold)

  def this(dataModel: DataModel, similarity: ItemSimilarity, booleanData: Boolean, neighbourSize: Int, threshold: Double) =
    this(dataModel, similarity, AbstractRecommender.getDefaultCandidateItemsStrategy(),
      GenericItemBasedRecommender.getDefaultMostSimilarItemsCandidateItemsStrategy(), booleanData, neighbourSize, threshold)

  @throws(classOf[TasteException])
  override def doEstimatePreference(userID: Long, preferencesFromUser: PreferenceArray, itemID: Long): Float = {
    val ratedIds = preferencesFromUser.getIDs()
      .zipWithIndex // need index for accessing preferencesFromUser later
      .map { case (id, index) => (id, similarity.itemSimilarity(itemID, id), index) } // (id, simiarity, index)
      .filter { case (id, sim, index) => (!sim.isNaN()) && (sim >= threshold) }

    val neighbourRatedIds = getTopN(ratedIds, neighbourSize)(RatedIdOdering.reverse)

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

  /* override default behavior which doesn't estimate if known preference */
  @throws(classOf[TasteException])
  override def estimatePreference(userID: Long, itemID: Long) = {
    val preferencesFromUser: PreferenceArray = getDataModel()
      .getPreferencesFromUser(userID)
    doEstimatePreference(userID, preferencesFromUser, itemID)
  }

  object RatedIdOdering extends Ordering[(Long, Double, Int)] {
    override def compare(a: (Long, Double, Int), b: (Long, Double, Int)) = a._2 compare b._2
  }

  def getTopN[T](s: Seq[T], n: Int)(implicit ord: Ordering[T]): Seq[T] = {
    val q = PriorityQueue()

    for (x <- s) {
      if (q.size < n)
        q.enqueue(x)
      else {
        // q is full
        if (ord.compare(x, q.head) < 0) {
          q.dequeue()
          q.enqueue(x)
        }
      }
    }

    q.dequeueAll.toSeq.reverse
  }

  override def toString() = {
    "KNNItemBasedRecommender"
  }
}
