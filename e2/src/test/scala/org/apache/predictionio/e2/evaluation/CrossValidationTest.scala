package org.apache.predictionio.e2.evaluation

import org.scalatest.{Matchers, Inspectors, FlatSpec}
import org.apache.spark.rdd.RDD
import org.apache.predictionio.e2.fixture.SharedSparkContext
import org.apache.predictionio.e2.engine.LabeledPoint

object CrossValidationTest {
  case class TrainingData(labeledPoints: Seq[LabeledPoint])
  case class Query(features: Array[String])
  case class ActualResult(label: String)

  case class EmptyEvaluationParams()

  def toTrainingData(labeledPoints: RDD[LabeledPoint]) = TrainingData(labeledPoints.collect().toSeq)
  def toQuery(labeledPoint: LabeledPoint) = Query(labeledPoint.features)
  def toActualResult(labeledPoint: LabeledPoint) = ActualResult(labeledPoint.label)

}


class CrossValidationTest extends FlatSpec with Matchers with Inspectors
with SharedSparkContext{


  val Label1 = "l1"
  val Label2 = "l2"
  val Label3 = "l3"
  val Label4 = "l4"
  val Attribute1 = "a1"
  val NotAttribute1 = "na1"
  val Attribute2 = "a2"
  val NotAttribute2 = "na2"

  val labeledPoints = Seq(
    LabeledPoint(Label1, Array(Attribute1, Attribute2)),
    LabeledPoint(Label2, Array(NotAttribute1, Attribute2)),
    LabeledPoint(Label3, Array(Attribute1, NotAttribute2)),
    LabeledPoint(Label4, Array(NotAttribute1, NotAttribute2))
  )

  val dataCount = labeledPoints.size
  val evalKs = (1 to dataCount)
  val emptyParams = new CrossValidationTest.EmptyEvaluationParams()
  type Fold = (
    CrossValidationTest.TrainingData,
    CrossValidationTest.EmptyEvaluationParams,
    RDD[(CrossValidationTest.Query, CrossValidationTest.ActualResult)])

  def toTestTrain(dataSplit: Fold): (Seq[LabeledPoint], Seq[LabeledPoint]) = {
    val trainingData = dataSplit._1.labeledPoints
    val queryActual = dataSplit._3
    val testingData = queryActual.map { case (query, actual) =>
      LabeledPoint(actual.label, query.features)
    }
    (trainingData, testingData.collect().toSeq)
  }

  def splitData(k: Int, labeledPointsRDD: RDD[LabeledPoint]): Seq[Fold] = {
    CommonHelperFunctions.splitData[
      LabeledPoint,
      CrossValidationTest.TrainingData,
      CrossValidationTest.EmptyEvaluationParams,
      CrossValidationTest.Query,
      CrossValidationTest.ActualResult](
        k,
        labeledPointsRDD,
        emptyParams,
        CrossValidationTest.toTrainingData,
        CrossValidationTest.toQuery,
        CrossValidationTest.toActualResult)
  }

  "Fold count" should "equal evalK" in {
    val labeledPointsRDD = sc.parallelize(labeledPoints)
    val lengths = evalKs.map(k => splitData(k, labeledPointsRDD).length)
    lengths should be(evalKs)
  }

  "Testing data size" should  "be within 1 of total / evalK" in {
    val labeledPointsRDD = sc.parallelize(labeledPoints)
    val splits = evalKs.map(k => k -> splitData(k, labeledPointsRDD))
    val diffs = splits.map { case (k, folds) =>
      folds.map(fold => fold._3.count() - dataCount / k)
    }
    forAll(diffs) {foldDiffs => foldDiffs.max should be <=  1L}
    diffs.map(folds => folds.sum) should be(evalKs.map(k => dataCount % k))
  }

  "Training + testing" should "equal original dataset" in {
    val labeledPointsRDD = sc.parallelize(labeledPoints)
    forAll(evalKs) {k =>
      val split = splitData(k, labeledPointsRDD)
      forAll(split) {fold =>
        val(training, testing) = toTestTrain(fold)
        (training ++ testing).toSet should be(labeledPoints.toSet)
      }
    }
  }

  "Training and testing" should "be disjoint" in {
    val labeledPointsRDD = sc.parallelize(labeledPoints)
    forAll(evalKs) { k =>
      val split = splitData(k, labeledPointsRDD)
      forAll(split) { fold =>
        val (training, testing) = toTestTrain(fold)
        training.toSet.intersect(testing.toSet) should be('empty)
      }
    }
  }
}
