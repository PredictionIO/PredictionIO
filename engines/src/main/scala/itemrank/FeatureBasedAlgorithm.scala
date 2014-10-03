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

package io.prediction.engines.itemrank

import io.prediction.controller._

import io.prediction.engines.base.PreparedData
import io.prediction.engines.base.RatingTD

import io.prediction.engines.itemrank._

import breeze.linalg.SparseVector
import breeze.linalg.inv

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

// FIXME. For now, we don't save the model.
case class FeatureBasedModel(
  val features: Array[String] = Array[String](),
  val userFeaturesMap: Map[String, SparseVector[Double]] =
    Map[String, SparseVector[Double]](),
  val itemFeaturesMap: Map[String, SparseVector[Double]] =
    Map[String, SparseVector[Double]]())
extends Serializable
with IPersistentModel[EmptyParams] {
  def save(id: String, p: EmptyParams, sc: SparkContext): Boolean = true
}

object FeatureBasedModel
extends IPersistentModelLoader[EmptyParams, FeatureBasedModel] {
  def apply(id: String, params: EmptyParams, sc: Option[SparkContext]) = {
    FeatureBasedModel()
  }
}

// FeatureBaseAlgorithm use all itypes as features.
class FeatureBasedAlgorithm
  extends LAlgorithm[EmptyParams, PreparedData, FeatureBasedModel,
      Query, Prediction] {

  def train(data: PreparedData): FeatureBasedModel = {
    val featureCounts = data.items
      .flatMap{ case(iindex, item) => item.itypes }
      .groupBy(identity)
      .mapValues(_.size)

    val features: Seq[String] = featureCounts.toSeq.sortBy(-_._2).map(_._1)
    val iFeatures: Seq[(Int, String)] = features.zipWithIndex.map(_.swap)

    val itemFeaturesMap: Map[String, SparseVector[Double]] =
    data.items.map { case(iindex, item) => {
      val itypes: Set[String] = item.itypes.toSet
      val raw: Seq[(Int, Double)] = iFeatures
        .filter{ case(i, f) => itypes(f) }
        .map{ case(i, f) => (i, 1.0) }

      val itemFeatures: SparseVector[Double] =
        SparseVector[Double](features.size)(raw:_*)
      (item.iid, itemFeatures :/ itemFeatures.sum)
    }}
    .toMap

    val userRatingsMap: Map[Int, Seq[RatingTD]] =
      data.rating.groupBy(_.uindex)

    val userFeaturesMap: Map[String, SparseVector[Double]] =
    userRatingsMap.mapValues { ratings => {
      val userFeatures: SparseVector[Double] = ratings
        .map(rating => data.items(rating.iindex).iid)
        .map(iid => itemFeaturesMap(iid))
        .reduce{ (a, b) => a + b }
      userFeatures
    }}
    .map { case(uindex, v) => (data.users(uindex).uid, v) }

    FeatureBasedModel(
      featureCounts.keys.toArray,
      userFeaturesMap,
      itemFeaturesMap
    )
  }

  def predict(model: FeatureBasedModel, query: Query): Prediction = {
    val (items, isOriginal): (Seq[(String, Double)], Boolean) = (
      if (model.userFeaturesMap.contains(query.uid)) {
        val userFeatures: SparseVector[Double] = model.userFeaturesMap(query.uid)

        val items: Seq[(String, Double)] = query.iids
        .map { iid => {
          val itemFeatures = model.itemFeaturesMap(iid)
          (iid, userFeatures.dot(itemFeatures))
        }}
        .sortBy(-_._2)
        (items, false)
      } else {
        // if user not found, use input order.
        (query.iids.map { iid => (iid, 0.0) }, true)
      }
    )
    new Prediction(items, isOriginal)
  }
}
