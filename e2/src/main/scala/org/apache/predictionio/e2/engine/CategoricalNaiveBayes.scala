/** Copyright 2015 TappingStone, Inc.
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
package org.apache.predictionio.e2.engine

import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

/**
 * Class for training a naive Bayes model with categorical variables
 */
object CategoricalNaiveBayes {
  /**
   * Train with data points and return the model
   *
   * @param points training data points
   */
  def train(points: RDD[LabeledPoint]): CategoricalNaiveBayesModel = {
    val labelCountFeatureLikelihoods = points.map { p =>
      (p.label, p.features)
    }.combineByKey[(Long, Array[Map[String, Long]])](
        createCombiner =
          (features: Array[String]) => {
            val featureCounts = features.map { feature =>
              Map[String, Long]().withDefaultValue(0L).updated(feature, 1L)
            }

            (1L, featureCounts)
          },
        mergeValue =
          (c: (Long, Array[Map[String, Long]]), features: Array[String]) => {
            (c._1 + 1L, c._2.zip(features).map { case (m, feature) =>
              m.updated(feature, m(feature) + 1L)
            })
          },
        mergeCombiners =
          (
            c1: (Long, Array[Map[String, Long]]),
            c2: (Long, Array[Map[String, Long]])) => {
            val labelCount1 = c1._1
            val labelCount2 = c2._1
            val featureCounts1 = c1._2
            val featureCounts2 = c2._2

            (labelCount1 + labelCount2,
              featureCounts1.zip(featureCounts2).map { case (m1, m2) =>
                m2 ++ m2.map { case (k, v) => k -> (v + m2(k))}
              })
          }
      ).mapValues { case (labelCount, featureCounts) =>
      val featureLikelihoods = featureCounts.map { featureCount =>
        // mapValues does not return a serializable map
        featureCount.mapValues(count => math.log(count.toDouble / labelCount))
          .map(identity)
      }

      (labelCount, featureLikelihoods)
    }.collect().toMap

    val noOfPoints = labelCountFeatureLikelihoods.map(_._2._1).sum
    val priors =
      labelCountFeatureLikelihoods.mapValues { countFeatureLikelihoods =>
        math.log(countFeatureLikelihoods._1 / noOfPoints.toDouble)
      }
    val likelihoods = labelCountFeatureLikelihoods.mapValues(_._2)

    CategoricalNaiveBayesModel(priors, likelihoods)
  }
}

/**
 * Model for naive Bayes classifiers with categorical variables.
 *
 * @param priors log prior probabilities
 * @param likelihoods log likelihood probabilities
 */
case class CategoricalNaiveBayesModel(
  priors: Map[String, Double],
  likelihoods: Map[String, Array[Map[String, Double]]]) extends Serializable {

  val featureCount = likelihoods.head._2.size

  /**
   * Calculate the log score of having the given features and label
   *
   * @param point label and features
   * @param defaultLikelihood a function that calculates the likelihood when a
   *                          feature value is not present. The input to the
   *                          function is the other feature value likelihoods.
   * @return log score when label is present. None otherwise.
   */
  def logScore(
    point: LabeledPoint,
    defaultLikelihood: (Seq[Double]) => Double = ls => Double.NegativeInfinity
    ): Option[Double] = {
    val label = point.label
    val features = point.features

    if (!priors.contains(label)) {
      None
    } else {
      Some(logScoreInternal(label, features, defaultLikelihood))
    }
  }

  private def logScoreInternal(
    label: String,
    features: Array[String],
    defaultLikelihood: (Seq[Double]) => Double = ls => Double.NegativeInfinity
    ): Double = {

    val prior = priors(label)
    val likelihood = likelihoods(label)

    val likelihoodScores = features.zip(likelihood).map {
      case (feature, featureLikelihoods) =>
        featureLikelihoods.getOrElse(
          feature,
          defaultLikelihood(featureLikelihoods.values.toSeq)
        )
    }

    prior + likelihoodScores.sum
  }

  /**
   * Return the label that yields the highest score
   *
   * @param features features for classification
   *
   */
  def predict(features: Array[String]): String = {
    priors.keySet.map { label =>
      (label, logScoreInternal(label, features))
    }.toSeq
      .sortBy(_._2)(Ordering.Double.reverse)
      .take(1)
      .head
      ._1
  }
}

/**
 * Class that represents the features and labels of a data point.
 *
 * @param label Label of this data point
 * @param features Features of this data point
 */
case class LabeledPoint(label: String, features: Array[String]) {
  override def toString: String = {
    val featuresString = features.mkString("[", ",", "]")

    s"($label, $featuresString)"
  }

  override def equals(other: Any): Boolean = other match {
    case that: LabeledPoint => that.toString == this.toString
    case _ => false
  }

  override def hashCode(): Int = {
    this.toString.hashCode
  }

}
