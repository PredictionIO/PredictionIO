package org.apache.predictionio.examples.stock

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.broadcast.Broadcast
import org.saddle._
import org.saddle.index.IndexTime
import com.github.nscala_time.time.Imports._
import scala.collection.immutable.HashMap

class RawData(
  val tickers: Array[String],
  val mktTicker: String,
  val timeIndex: Array[DateTime],
  private[stock] val _price: Array[(String, Array[Double])],
  private[stock] val _active: Array[(String, Array[Boolean])])
    extends Serializable {

  @transient lazy val _priceFrame: Frame[DateTime, String, Double] =
    SaddleWrapper.ToFrame(timeIndex, _price)

  // FIXME. Fill NA of result.
  @transient lazy val _retFrame: Frame[DateTime, String, Double] = 
    _priceFrame.shift(1) / _priceFrame
  
  @transient lazy val _activeFrame: Frame[DateTime, String, Boolean] =
    SaddleWrapper.ToFrame(timeIndex, _active)

  def view(idx: Int, maxWindowSize: Int): DataView = 
    DataView(this, idx, maxWindowSize)

  override def toString(): String = {
    val timeHead = timeIndex.head
    val timeLast = timeIndex.last
    s"RawData[$timeHead, $timeLast, $mktTicker, size=${tickers.size}]"
  }
}

// A data view of RawData from [idx - maxWindowSize + 1 : idx]
// Notice that the last day is *inclusive*.
// This clas takes the whole RawData reference, hence should *not* be serialized
case class DataView(val rawData: RawData, val idx: Int, val maxWindowSize: Int) {
  def today(): DateTime = rawData.timeIndex(idx)

  val tickers = rawData.tickers
  val mktTicker = rawData.mktTicker
  
  def priceFrame(windowSize: Int = 1)
  : Frame[DateTime, String, Double] = {
    // Check windowSize <= maxWindowSize
    rawData._priceFrame.rowSlice(idx - windowSize + 1, idx + 1)
  }
  
  def retFrame(windowSize: Int = 1)
  : Frame[DateTime, String, Double] = {
    // Check windowSize <= maxWindowSize
    rawData._retFrame.rowSlice(idx - windowSize + 1, idx + 1)
  }
  
  def activeFrame(windowSize: Int = 1)
  : Frame[DateTime, String, Boolean] = {
    // Check windowSize <= maxWindowSize
    rawData._activeFrame.rowSlice(idx - windowSize + 1, idx + 1)
  }

  override def toString(): String = {
    priceFrame().toString
  }
}


// Training data visible to the user is [untilIdx - windowSize, untilIdx).
case class TrainingData(
  untilIdx: Int,
  maxWindowSize: Int,
  rawDataB: Broadcast[RawData]) {
 
  def view(): DataView = DataView(rawDataB.value, untilIdx - 1, maxWindowSize)
}

case class DataParams(rawDataB: Broadcast[RawData])

// Date
case class QueryDate(idx: Int)

case class Query(
  val idx: Int,
  val dataView: DataView,
  val tickers: Array[String],
  val mktTicker: String)

// Prediction
case class Prediction(data: HashMap[String, Double])

object SaddleWrapper {
  def ToFrame[A](
    timeIndex: Array[DateTime],
    tickerPriceSeq: Array[(String, Array[A])]
    )(implicit st: ST[A])
  : Frame[DateTime, String, A] = {
    val index = IndexTime(timeIndex:_ *)
    val seriesList = tickerPriceSeq.map{ case(ticker, price) => {
      val series = Series(Vec(price), index)
      (ticker, series)
    }}
    Frame(seriesList:_*)
  }

  def FromFrame[A](data: Frame[DateTime, String, A]
    ): (Array[DateTime], Array[(String, Array[A])]) = {
    val timeIndex = data.rowIx.toVec.contents
    val tickers = data.colIx.toVec.contents

    val tickerDataSeq = tickers.map{ ticker => {
      (ticker, data.firstCol(ticker).toVec.contents)
    }}

    (timeIndex, tickerDataSeq)
  }
}
