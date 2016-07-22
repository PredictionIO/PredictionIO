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
import org.apache.spark.mllib.linalg.distributed.CoordinateMatrix
import org.apache.spark.mllib.linalg.{SparseVector, Vectors}
import org.apache.spark.rdd.RDD

/**
 * Class for training a Markov Chain model
 */
object MarkovChain {
  /**
   * Train a Markov Chain model
   *
   * @param matrix Tally of all state transitions
   * @param topN Use the top-N tally for each state
   */
  def train(matrix: CoordinateMatrix, topN: Int): MarkovChainModel = {
    val noOfStates = matrix.numCols().toInt
    val transitionVectors = matrix.entries
      .keyBy(_.i.toInt)
      .groupByKey()
      .mapValues { rowEntries =>
      val total = rowEntries.map(_.value).sum
      val sortedTopN = rowEntries.toSeq
        .sortBy(_.value)(Ordering.Double.reverse)
        .take(topN)
        .map(me => (me.j.toInt, me.value / total))
        .sortBy(_._1)

      new SparseVector(
        noOfStates,
        sortedTopN.map(_._1).toArray,
        sortedTopN.map(_._2).toArray)
    }

    new MarkovChainModel(
      transitionVectors,
      topN)
  }
}

/**
 * Markov Chain model
 *
 * @param transitionVectors transition vectors
 * @param n top N used to construct the model
 */
case class MarkovChainModel(
  transitionVectors: RDD[(Int, SparseVector)],
  n: Int) {

  /**
   * Calculate the probabilities of the next state
   *
   * @param currentState probabilities of the current state
   */
  def predict(currentState: Seq[Double]): Seq[Double] = {
    // multiply the input with transition matrix row by row
    val nextStateVectors = transitionVectors.map { case (rowIndex, vector) =>
        val values = vector.indices.map { index =>
          vector(index) * currentState(rowIndex)
        }

        Vectors.sparse(currentState.size, vector.indices, values)
    }.collect()

    // sum up to get the total probabilities
    (0 until currentState.size).map { index =>
      nextStateVectors.map { vector =>
        vector(index)
      }.sum
    }
  }
}
