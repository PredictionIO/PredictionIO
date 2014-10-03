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

package io.prediction.engines.base.mahout;

import org.apache.mahout.cf.taste.impl.recommender.AbstractCandidateItemsStrategy;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.impl.model.GenericBooleanPrefDataModel;

public final class AllValidItemsCandidateItemsStrategy extends AbstractCandidateItemsStrategy {

  private final FastByIDMap<FastIDSet> seenDataMap;
  private final long[] validItemIDs;
  /*
   * @param validItemIDs valid item IDs
   * @param seenDataModel set this to null if don't exclude seen items
   */
  public AllValidItemsCandidateItemsStrategy(long[] validItemIDs,
    DataModel seenDataModel) throws TasteException {
      super();
      if (seenDataModel != null)
        this.seenDataMap = GenericBooleanPrefDataModel.toDataMap(
          seenDataModel);
      else
        this.seenDataMap = null;

      this.validItemIDs = validItemIDs;
  }

  // include seen items as candidate items
  public AllValidItemsCandidateItemsStrategy(long[] validItemIDs)
    throws TasteException {
      this(validItemIDs, null);
  }

  @Override
  public FastIDSet getCandidateItems(long userID,
    PreferenceArray preferencesFromUser, DataModel dataModel)
    throws TasteException {
      long[] seenItemIDs;
      if (this.seenDataMap != null) {
        FastIDSet ids = seenDataMap.get(userID);
        if (ids != null) {
          seenItemIDs = ids.toArray();
        } else {
          // user doesn't have any seen action
          seenItemIDs = null;
        }
      } else {
        seenItemIDs = null;
      }

      return doGetCandidateItemsInternal(this.validItemIDs, seenItemIDs);
  }

  protected FastIDSet doGetCandidateItemsInternal(long[] validItemIDs,
    long[] seenItemIDs) throws TasteException {
      FastIDSet possibleItemsIDs = new FastIDSet();
      possibleItemsIDs.addAll(this.validItemIDs);

      if (seenItemIDs != null)
        possibleItemsIDs.removeAll(seenItemIDs);

      return possibleItemsIDs;
  }

  // override for AbstractCandidateItemsStrategy
  @Override
  protected FastIDSet doGetCandidateItems(long[] preferredItemIDs,
    DataModel dataModel) throws TasteException {
      return doGetCandidateItemsInternal(this.validItemIDs, null);
  }

}
