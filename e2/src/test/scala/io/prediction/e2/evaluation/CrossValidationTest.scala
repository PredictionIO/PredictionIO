package io.prediction.e2.evaluation

import io.prediction.e2.evaluation.CrossValidationTest.EmptyParams
import org.scalatest.{Matchers, FlatSpec}
import org.apache.spark.rdd.RDD
import io.prediction.e2.fixture.SharedSparkContext
import io.prediction.e2.engine.LabeledPoint



import scala.language.reflectiveCalls

object CrossValidationTest {
  case class TrainingData(labeledPoints: Seq[LabeledPoint])
  case class Query(features: Array[String])
  case class ActualResult(label: String)

  class EmptyParams extends Serializable {}

  def toTrainingData(labeledPoints: RDD[LabeledPoint]) = TrainingData(labeledPoints.collect().toSeq)
  def toQuery(labeledPoint: LabeledPoint) = Query(labeledPoint.features)
  def toActualResult(labeledPoint: LabeledPoint) = ActualResult(labeledPoint.label)

}


class CrossValidationTest extends FlatSpec with Matchers
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
  val emptyParams = new CrossValidationTest.EmptyParams()
  type D = LabeledPoint
  type TD = CrossValidationTest.TrainingData
  type EI = CrossValidationTest.EmptyParams
  type Q = CrossValidationTest.Query
  type A = CrossValidationTest.ActualResult
  type Fold = (TD, EI, RDD[(Q, A)])

  def toTestTrain(dataSplit: Fold): (Seq[D], Seq[D]) = {
    val trainingData = dataSplit._1.labeledPoints
    val queryActual = dataSplit._3
    val testingData = queryActual.map { case (query: Q, actual: A) =>
      LabeledPoint(actual.label, query.features)
    }
    (trainingData, testingData.collect().toSeq)
  }

  def splitData(k: Int, labeledPointsRDD: RDD[LabeledPoint]): Seq[Fold] = {
    CommonHelperFunctions.splitData[D, TD, EI, Q, A](
      k,
      labeledPointsRDD,
      emptyParams,
      CrossValidationTest.toTrainingData,
      CrossValidationTest.toQuery,
      CrossValidationTest.toActualResult)
  }



  "Fold count" should "equal evalK" in {
    val labeledPointsRDD = sc.parallelize(labeledPoints)
    val splits = evalKs.map {k => k -> splitData(k, labeledPointsRDD)}
    splits.map { case (k, folds) =>
        folds.length should be(k)
    }
  }


  "Testing data size" should  "equal total / evalK" in {
    val labeledPointsRDD = sc.parallelize(labeledPoints)
    val splits = evalKs.map {k => k -> splitData(k, labeledPointsRDD)}
    splits.map {case (k, folds) =>
      folds.map {fold: Fold =>
        val foldSize = dataCount / k
        if (dataCount % k == 0) {
          fold._3.count() should be(foldSize)
        }
        else {
          val diff = fold._3.count() - foldSize
          diff should be <= 1L
        }
      }
    }
  }

  "Training + testing" should "equal original dataset" in {
    val labeledPointsRDD = sc.parallelize(labeledPoints)
    val splits = evalKs.map {k => k -> splitData(k, labeledPointsRDD)}
    splits.map {case (k, folds) =>
      folds.map {fold: Fold =>
        val (training, testing) = toTestTrain(fold)
        (training ++ testing).toSet should equal (labeledPoints.toSet)
      }
    }
  }

  "Training and testing" should "be disjoint" in {
    val labeledPointsRDD = sc.parallelize(labeledPoints)
    val splits = evalKs.map {k => k -> splitData(k, labeledPointsRDD)}
    splits.map { case (k, folds) =>
      folds.map { fold: Fold =>
        val (training, testing) = toTestTrain(fold)
        training.toSet.intersect(testing.toSet) should be('empty)
      }
    }
  }
}
