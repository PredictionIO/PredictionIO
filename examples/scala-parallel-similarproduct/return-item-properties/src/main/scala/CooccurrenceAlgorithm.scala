/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.predictionio.examples.similarproduct

import org.apache.predictionio.controller.P2LAlgorithm
import org.apache.predictionio.controller.Params
import org.apache.predictionio.data.storage.BiMap

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

case class CooccurrenceAlgorithmParams(
  n: Int // top co-occurrence
) extends Params

class CooccurrenceModel(
  val topCooccurrences: Map[Int, Array[(Int, Int)]],
  val itemStringIntMap: BiMap[String, Int],
  val items: Map[Int, Item]
) extends Serializable {
  @transient lazy val itemIntStringMap = itemStringIntMap.inverse

  override def toString(): String = {
    val s = topCooccurrences.mapValues { v => v.mkString(",") }
    s.toString
  }
}

class CooccurrenceAlgorithm(val ap: CooccurrenceAlgorithmParams)
  extends P2LAlgorithm[PreparedData, CooccurrenceModel, Query, PredictedResult] {

  def train(sc: SparkContext, data: PreparedData): CooccurrenceModel = {

    val itemStringIntMap = BiMap.stringInt(data.items.keys)

    val topCooccurrences = trainCooccurrence(
      events = data.viewEvents,
      n = ap.n,
      itemStringIntMap = itemStringIntMap
    )

    // collect Item as Map and convert ID to Int index
    val items: Map[Int, Item] = data.items.map { case (id, item) =>
      (itemStringIntMap(id), item)
    }.collectAsMap.toMap

    new CooccurrenceModel(
      topCooccurrences = topCooccurrences,
      itemStringIntMap = itemStringIntMap,
      items = items
    )

  }

  /* given the user-item events, find out top n co-occurrence pair for each item */
  def trainCooccurrence(
    events: RDD[ViewEvent],
    n: Int,
    itemStringIntMap: BiMap[String, Int]): Map[Int, Array[(Int, Int)]] = {

    val userItem = events
      // map item from string to integer index
      .flatMap {
        case ViewEvent(user, item, _) if itemStringIntMap.contains(item) =>
          Some(user, itemStringIntMap(item))
        case _ => None
      }
      // if user view same item multiple times, only count as once
      .distinct()
      .cache()

    val cooccurrences: RDD[((Int, Int), Int)] = userItem.join(userItem)
      // remove duplicate pair in reversed order for each user. eg. (a,b) vs. (b,a)
      .filter { case (user, (item1, item2)) => item1 < item2 }
      .map { case (user, (item1, item2)) => ((item1, item2), 1) }
      .reduceByKey{ (a: Int, b: Int) => a + b }

    val topCooccurrences = cooccurrences
      .flatMap{ case (pair, count) =>
        Seq((pair._1, (pair._2, count)), (pair._2, (pair._1, count)))
      }
      .groupByKey
      .map { case (item, itemCounts) =>
        (item, itemCounts.toArray.sortBy(_._2)(Ordering.Int.reverse).take(n))
      }
      .collectAsMap.toMap

    topCooccurrences
  }

  def predict(model: CooccurrenceModel, query: Query): PredictedResult = {

    // convert items to Int index
    val queryList: Set[Int] = query.items
      .flatMap(model.itemStringIntMap.get(_))
      .toSet

    val whiteList: Option[Set[Int]] = query.whiteList.map( set =>
      set.map(model.itemStringIntMap.get(_)).flatten
    )

    val blackList: Option[Set[Int]] = query.blackList.map ( set =>
      set.map(model.itemStringIntMap.get(_)).flatten
    )

    val counts: Array[(Int, Int)] = queryList.toVector
      .flatMap { q =>
        model.topCooccurrences.getOrElse(q, Array())
      }
      .groupBy { case (index, count) => index }
      .map { case (index, indexCounts) => (index, indexCounts.map(_._2).sum) }
      .toArray

    val itemScores = counts
      .filter { case (i, v) =>
        isCandidateItem(
          i = i,
          items = model.items,
          categories = query.categories,
          queryList = queryList,
          whiteList = whiteList,
          blackList = blackList
        )
      }
      .sortBy(_._2)(Ordering.Int.reverse)
      .take(query.num)
      .map { case (index, count) =>
        // MODIFIED
        val it = model.items(index)
        ItemScore(
          item = model.itemIntStringMap(index),
          title = it.title,
          date = it.date,
          imdbUrl = it.imdbUrl,
          score = count
        )
      }

    PredictedResult(itemScores)

  }

  private
  def isCandidateItem(
    i: Int,
    items: Map[Int, Item],
    categories: Option[Set[String]],
    queryList: Set[Int],
    whiteList: Option[Set[Int]],
    blackList: Option[Set[Int]]
  ): Boolean = {
    whiteList.map(_.contains(i)).getOrElse(true) &&
    blackList.map(!_.contains(i)).getOrElse(true) &&
    // discard items in query as well
    (!queryList.contains(i)) &&
    // filter categories
    categories.map { cat =>
      items(i).categories.map { itemCat =>
        // keep this item if has ovelap categories with the query
        !(itemCat.toSet.intersect(cat).isEmpty)
      }.getOrElse(false) // discard this item if it has no categories
    }.getOrElse(true)
  }

}
