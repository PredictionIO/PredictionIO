package org.apache.predictionio.examples.stock

import org.apache.predictionio.controller.Params

import org.saddle._
import org.saddle.index.IndexTime

import scala.collection.immutable.HashMap
import breeze.linalg.DenseMatrix
import breeze.linalg.DenseVector

import com.github.nscala_time.time.Imports._

import scala.math

import nak.regress.LinearRegression

/** Regression Strategy parameters case class
  *
  * @param indicators a sequence of tuples. The first element is a string that
  *        can be helpful in acting as a label for the indicator,
  *        such as for debugging. Pass in empty string if no label
  *        desired.
  * @param maxTrainingWindowSize maximum window size of price frame desired
  *        for training
  */
case class RegressionStrategyParams (
  indicators: Seq[(String, BaseIndicator)],
  maxTrainingWindowSize: Int
) extends Params

class RegressionStrategy (params: RegressionStrategyParams) 
  extends StockStrategy[Map[String, DenseVector[Double]]] {

  private def getRet(logPrice: Frame[DateTime, String, Double], d: Int) =
    (logPrice - logPrice.shift(d)).mapVec[Double](_.fillNA(_ => 0.0))

  // Regress on specific ticker
  private def regress(
    calculatedData: Seq[Series[DateTime, Double]],
    retF1d: Series[DateTime, Double]) = {
    val array = (
      calculatedData.map(_.toVec.contents).reduce(_++_) ++
      Array.fill(retF1d.length)(1.0)).toArray[Double]
    val target = DenseVector[Double](retF1d.toVec.contents)
    val m = DenseMatrix.create[Double](
      retF1d.length, 
      calculatedData.length + 1, 
      array
    )
    val result = LinearRegression.regress(m, target)
    result
  }

  // Compute each indicator value for training the model
  private def calcIndicator(logPrice: Series[DateTime, Double]):
     Seq[Series[DateTime, Double]] = {
    params.indicators.map { 
      case(name, indicator) => indicator.getTraining(logPrice)
    }
  }

  // Get max period from series of indicators
  private def getMaxPeriod() : Int = {
    // create an array of periods
    val periods = params.indicators.map { 
      case(name, indicator) => indicator.getMinWindowSize() 
    }
    periods.max
  }

  // Apply regression algorithm on complete dataset to create a model
  def createModel(dataView: DataView): Map[String, DenseVector[Double]] = {
    // price: row is time, col is ticker, values are prices
    val price = dataView.priceFrame(params.maxTrainingWindowSize)
    val logPrice = price.mapValues(math.log)
    val active = dataView.activeFrame(params.maxTrainingWindowSize)

    // value used to query prediction results
    val retF1d = getRet(logPrice, -1)

    val timeIndex = price.rowIx
    val firstIdx = getMaxPeriod() + 3
    val lastIdx = timeIndex.length

    // Get array of ticker strings
    val tickers = price.colIx.toVec.contents

    // For each active ticker, pass in trained series into regress
    val tickerModelMap = tickers
    .filter(ticker => (active.firstCol(ticker).findOne(_ == false) == -1))
    .map(ticker => {
      val model = regress(
        calcIndicator(price.firstCol(ticker)).map(_.slice(firstIdx, lastIdx)),
        retF1d.firstCol(ticker).slice(firstIdx, lastIdx))
      (ticker, model)
    }).toMap

    // tickers mapped to model
    tickerModelMap
  }

  // returns a prediction for a specific ticker
  private def predictOne(
    coef: DenseVector[Double],
    ticker: String,
    dataView: DataView): Double = {

    val vecArray = params.indicators.map { case (name, indicator) => {
      val price = dataView.priceFrame(indicator.getMinWindowSize())
      val logPrice = price.mapValues(math.log)
      indicator.getOne(logPrice.firstCol(ticker))
    }}.toArray

    val densVecArray = vecArray ++ Array[Double](1)
    val vec = DenseVector[Double](densVecArray)
  
    val p = coef.dot(vec)
    return p
  }

  // Returns a mapping of tickers to predictions
  def onClose(model: Map[String, DenseVector[Double]], query: Query)
  : Prediction = {
    val dataView = query.dataView

    val prediction = query.tickers
      .filter(ticker => model.contains(ticker))
      .map { ticker => {
        val p = predictOne(
          model(ticker),
          ticker,
          dataView)
        (ticker, p)
      }}

    Prediction(HashMap[String, Double](prediction:_*))
  }
}
