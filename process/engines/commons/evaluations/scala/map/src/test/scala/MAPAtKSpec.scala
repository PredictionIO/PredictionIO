package io.prediction.metrics.itemrec.map

import com.mongodb.casbah.Imports._
import org.specs2._
import org.specs2.specification.Step

class MAPAtKSpec extends Specification {
  def is = s2"""

  MAP@k Specification

  Computing AP@k should be correct
    Test 1 $apatk1
    Test 2 $apatk2
    Test 3 $apatk3
    Test 4 $apatk4

  At the end of test it should
    clean up test database cleanup
  """

  def apatk1 =
    MAPAtK.averagePrecisionAtK(
      5,
      Seq("foo", "bar", "abc", "def", "ghi"),
      Set("bar", "def")) must_== 0.5

  def apatk2 =
    MAPAtK.averagePrecisionAtK(
      10,
      Seq("foo", "bar", "abc", "def", "ghi"),
      Set("bar", "def")) must_== 0.5

  def apatk3 =
    MAPAtK.averagePrecisionAtK(
      10,
      Seq("a", "b", "c", "d", "e", "f", "g", "h"),
      Set("a", "e", "c")) must_== 0.7555555555555555

  def apatk4 =
    MAPAtK.averagePrecisionAtK(
      4,
      Seq("a", "b", "c", "d", "e", "f", "g", "h"),
      Set("a", "e", "c")) must_== 0.5555555555555555

  def cleanup = {
    Step(MongoConnection()("predictionio_mapatk_test").dropDatabase())
    Step(MongoConnection()("predictionio_appdata_mapatk_test").dropDatabase())
    Step(MongoConnection()("predictionio_modeldata_mapatk_test").dropDatabase())
    Step(MongoConnection()("predictionio_training_appdata_mapatk_test").dropDatabase())
    Step(MongoConnection()("predictionio_validation_appdata_mapatk_test").dropDatabase())
    Step(MongoConnection()("predictionio_test_appdata_mapatk_test").dropDatabase())
    Step(MongoConnection()("predictionio_training_modeldata_mapatk_test").dropDatabase())
  }
}
