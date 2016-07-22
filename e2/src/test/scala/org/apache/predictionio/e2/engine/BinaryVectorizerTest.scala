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

import org.apache.predictionio.e2.fixture.BinaryVectorizerFixture
import org.apache.predictionio.e2.fixture.SharedSparkContext
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.rdd.RDD
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import scala.collection.immutable.HashMap


import scala.language.reflectiveCalls

class BinaryVectorizerTest extends FlatSpec with Matchers with SharedSparkContext
with BinaryVectorizerFixture{

  "toBinary" should "produce the following summed values:" in {
    val testCase = BinaryVectorizer(sc.parallelize(base.maps), base.properties)
    val vectorTwoA = testCase.toBinary(testArrays.twoA)
    val vectorTwoB = testCase.toBinary(testArrays.twoB)


    // Make sure vectors produced are the same size.
    vectorTwoA.size should be (vectorTwoB.size)

    // // Test case for checking food value not listed in base.maps.
    testCase.toBinary(testArrays.one).toArray.sum should be (1.0)

    // Test cases for making sure indices are preserved.
    val sumOne = vecSum(vectorTwoA, vectorTwoB)

    exactly (1, sumOne) should be (2.0)
    exactly (2,sumOne) should be (0.0)
    exactly (2, sumOne) should be (1.0)

    val sumTwo = vecSum(Vectors.dense(sumOne), testCase.toBinary(testArrays.twoC))

    exactly (3, sumTwo) should be (1.0)
  }

}
