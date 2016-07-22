/** Copyright 2015 TappingStone, Inc.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package org.apache.predictionio.e2.engine

import org.apache.predictionio.e2.fixture.{NaiveBayesFixture, SharedSparkContext}
import org.scalatest.{Matchers, FlatSpec}

import scala.language.reflectiveCalls

class CategoricalNaiveBayesTest extends FlatSpec with Matchers
with SharedSparkContext with NaiveBayesFixture {
  val Tolerance = .0001
  val labeledPoints = fruit.labeledPoints

  "Model" should "have log priors and log likelihoods" in {
    val labeledPointsRdd = sc.parallelize(labeledPoints)
    val model = CategoricalNaiveBayes.train(labeledPointsRdd)

    model.priors(fruit.Banana) should be(-.7885 +- Tolerance)
    model.priors(fruit.Orange) should be(-1.7047 +- Tolerance)
    model.priors(fruit.OtherFruit) should be(-1.0116 +- Tolerance)

    model.likelihoods(fruit.Banana)(0)(fruit.Long) should
      be(-.2231 +- Tolerance)
    model.likelihoods(fruit.Banana)(0)(fruit.NotLong) should
      be(-1.6094 +- Tolerance)
    model.likelihoods(fruit.Banana)(1)(fruit.Sweet) should
      be(-.2231 +- Tolerance)
    model.likelihoods(fruit.Banana)(1)(fruit.NotSweet) should
      be(-1.6094 +- Tolerance)
    model.likelihoods(fruit.Banana)(2)(fruit.Yellow) should
      be(-.2231 +- Tolerance)
    model.likelihoods(fruit.Banana)(2)(fruit.NotYellow) should
      be(-1.6094 +- Tolerance)

    model.likelihoods(fruit.Orange)(0) should not contain key(fruit.Long)
    model.likelihoods(fruit.Orange)(0)(fruit.NotLong) should be(0.0)
    model.likelihoods(fruit.Orange)(1)(fruit.Sweet) should
      be(-.6931 +- Tolerance)
    model.likelihoods(fruit.Orange)(1)(fruit.NotSweet) should
      be(-.6931 +- Tolerance)
    model.likelihoods(fruit.Orange)(2)(fruit.NotYellow) should be(0.0)
    model.likelihoods(fruit.Orange)(2) should not contain key(fruit.Yellow)

    model.likelihoods(fruit.OtherFruit)(0)(fruit.Long) should
      be(-.6931 +- Tolerance)
    model.likelihoods(fruit.OtherFruit)(0)(fruit.NotLong) should
      be(-.6931 +- Tolerance)
    model.likelihoods(fruit.OtherFruit)(1)(fruit.Sweet) should
      be(-.2877 +- Tolerance)
    model.likelihoods(fruit.OtherFruit)(1)(fruit.NotSweet) should
      be(-1.3863 +- Tolerance)
    model.likelihoods(fruit.OtherFruit)(2)(fruit.Yellow) should
      be(-1.3863 +- Tolerance)
    model.likelihoods(fruit.OtherFruit)(2)(fruit.NotYellow) should
      be(-.2877 +- Tolerance)
  }

  "Model's log score" should "be the log score of the given point" in {
    val labeledPointsRdd = sc.parallelize(labeledPoints)
    val model = CategoricalNaiveBayes.train(labeledPointsRdd)

    val score = model.logScore(LabeledPoint(
      fruit.Banana,
      Array(fruit.Long, fruit.NotSweet, fruit.NotYellow))
    )

    score should not be None
    score.get should be(-4.2304 +- Tolerance)
  }

  it should "be negative infinity for a point with a non-existing feature" in {
    val labeledPointsRdd = sc.parallelize(labeledPoints)
    val model = CategoricalNaiveBayes.train(labeledPointsRdd)

    val score = model.logScore(LabeledPoint(
      fruit.Banana,
      Array(fruit.Long, fruit.NotSweet, "Not Exist"))
    )

    score should not be None
    score.get should be(Double.NegativeInfinity)
  }

  it should "be none for a point with a non-existing label" in {
    val labeledPointsRdd = sc.parallelize(labeledPoints)
    val model = CategoricalNaiveBayes.train(labeledPointsRdd)

    val score = model.logScore(LabeledPoint(
      "Not Exist",
      Array(fruit.Long, fruit.NotSweet, fruit.Yellow))
    )

    score should be(None)
  }

  it should "use the provided default likelihood function" in {
    val labeledPointsRdd = sc.parallelize(labeledPoints)
    val model = CategoricalNaiveBayes.train(labeledPointsRdd)

    val score = model.logScore(
      LabeledPoint(
        fruit.Banana,
        Array(fruit.Long, fruit.NotSweet, "Not Exist")
      ),
      ls => ls.min - math.log(2)
    )

    score should not be None
    score.get should be(-4.9236 +- Tolerance)
  }

  "Model predict" should "return the correct label" in {
    val labeledPointsRdd = sc.parallelize(labeledPoints)
    val model = CategoricalNaiveBayes.train(labeledPointsRdd)

    val label = model.predict(Array(fruit.Long, fruit.Sweet, fruit.Yellow))
    label should be(fruit.Banana)
  }
}
