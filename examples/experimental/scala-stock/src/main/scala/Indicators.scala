package org.apache.predictionio.examples.stock

import org.saddle._
import org.saddle.index.IndexTime

import scala.collection.immutable.HashMap
import breeze.linalg.DenseMatrix
import breeze.linalg.DenseVector

import com.github.nscala_time.time.Imports._

import scala.math

import math._

import nak.regress.LinearRegression

/**
  * Base class for an indicator.
  *
  * All indicators should be defined as classes that extend
  * this base class. See RSIIndicator as an example. These indicators can then
  * be instantiated and passed into a StockStrategy class. Refer to tutorial 
  * for further explanation (found in the README.md file).
  */
@SerialVersionUID(100L)
abstract class BaseIndicator extends Serializable {
  /** Calculates training series for a particular stock.
    *
    * @param logPrice series of logarithm of all prices for a particular stock.
    *         Logarithm values are recommended for more accurate results.
    * @return the training series of the stock
    */
  def getTraining(logPrice: Series[DateTime, Double]): Series[DateTime, Double]

  /** Applies indicator on a window size of the value returned by 
    * getMinWindowSize() and returns the last value in the resulting series to
    * be used for prediction in RegressionStrategy.
    *
    * @param logPrice series of logarithm of all prices for a particular stock
    * @return the last value in the resulting series from the feature 
    *           calculation
    */
  def getOne(input: Series[DateTime, Double]): Double

  /** Returns window size to be used in getOne()
    *
    * @return the window size
    */
  def getMinWindowSize(): Int
}

/** Indicator that implements a relative strength index formula
  *
  * @constructor create an instance of an RSIIndicator
  * @param period number of days to use for each of the 14 periods
  *         that are used in the RSI calculation
  */
class RSIIndicator(rsiPeriod: Int = 14) extends BaseIndicator {

  private def getRet(dailyReturn: Series[DateTime, Double]) =
    (dailyReturn - dailyReturn.shift(1)).fillNA(_ => 0.0)

  def getMinWindowSize(): Int = rsiPeriod + 1

  private def calcRS(logPrice: Series[DateTime, Double])
    : Series[DateTime, Double] = {
    //Positive and Negative Vecs
    val posSeries = logPrice.mapValues[Double]((x: Double) 
      => if (x > 0) x else 0)
    val negSeries = logPrice.mapValues[Double]((x: Double) 
      => if (x < 0) x else 0)
    
    //Get the sum of positive/negative Frame
    val avgPosSeries = 
      posSeries.rolling[Double] (rsiPeriod, (f: Series[DateTime,Double]) 
        => f.mean)
    val avgNegSeries = 
      negSeries.rolling[Double] (rsiPeriod, (f: Series[DateTime,Double]) 
        => f.mean)

    val rsSeries = avgPosSeries / avgNegSeries
    rsSeries
  }

  // Computes RSI of price data over the defined training window time frame
  def getTraining(logPrice: Series[DateTime, Double])
    : Series[DateTime, Double] = {
    val rsSeries = calcRS(getRet(logPrice))
    val rsiSeries = rsSeries.mapValues[Double]( 
        (x:Double) => 100 - ( 100 / (1 + x)))

    // Fill in first 14 days offset with 50 to maintain results
    rsiSeries.reindex(logPrice.rowIx).fillNA(_  => 50.0)
  }

    // Computes the RSI for the most recent time frame, returns single double
  def getOne(logPrice: Series[DateTime, Double]): Double = {
    getTraining(logPrice).last
  }
}

/** Indicator that calcuate differences of closing prices
  *
  * @constructor create an instance of a ShiftsIndicator
  * @param period number of days between any 2 closing prices to consider for 
  *          calculating a return
  */
class ShiftsIndicator(period: Int) extends BaseIndicator {

  private def getRet(logPrice: Series[DateTime, Double], frame: Int = period) =
   (logPrice - logPrice.shift(frame)).fillNA(_ => 0.0)

  def getMinWindowSize(): Int = period + 1

  def getTraining(logPrice: Series[DateTime, Double])
    : Series[DateTime, Double] = {
    getRet(logPrice)
  }

  def getOne(logPrice: Series[DateTime, Double]): Double = {
    getRet(logPrice).last
  }
}
