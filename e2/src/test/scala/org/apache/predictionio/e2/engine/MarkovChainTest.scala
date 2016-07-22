package org.apache.predictionio.e2.engine

import org.apache.predictionio.e2.fixture.{MarkovChainFixture, SharedSparkContext}
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.linalg.distributed.CoordinateMatrix
import org.scalatest.{FlatSpec, Matchers}

import scala.language.reflectiveCalls

class MarkovChainTest extends FlatSpec with Matchers with SharedSparkContext
with MarkovChainFixture {

  "Markov chain training" should "produce a model" in {
    val matrix =
      new CoordinateMatrix(sc.parallelize(twoByTwoMatrix.matrixEntries))
    val model = MarkovChain.train(matrix, 2)

    model.n should be(2)
    model.transitionVectors.collect() should contain theSameElementsAs Seq(
      (0, Vectors.sparse(2, Array(0, 1), Array(0.3, 0.7))),
      (1, Vectors.sparse(2, Array(0, 1), Array(0.5, 0.5)))
    )
  }

  it should "contains probabilities of the top N only" in {
    val matrix =
      new CoordinateMatrix(sc.parallelize(fiveByFiveMatrix.matrixEntries))
    val model = MarkovChain.train(matrix, 2)

    model.n should be(2)
    (0, Vectors.sparse(5, Array(1, 2), Array(.6, .4)))
    model.transitionVectors.collect() should contain theSameElementsAs Seq(
      (0, Vectors.sparse(5, Array(1, 2), Array(.6, .4))),
      (1, Vectors.sparse(5, Array(2, 4), Array(9.0 / 25, 8.0 / 25))),
      (2, Vectors.sparse(5, Array(1, 4), Array(10.0 / 28, 10.0 / 28))),
      (3, Vectors.sparse(5, Array(3, 4), Array(3.0 / 9, 4.0 / 9))),
      (4, Vectors.sparse(5, Array(3, 4), Array(8.0 / 25, 0.4)))
    )
  }

  "Model predict" should "calculate the probablities of new states" in {
    val matrix =
      new CoordinateMatrix(sc.parallelize(twoByTwoMatrix.matrixEntries))
    val model = MarkovChain.train(matrix, 2)
    val nextState = model.predict(Seq(0.4, 0.6))

    nextState should contain theSameElementsInOrderAs Seq(0.42, 0.58)
  }
}
