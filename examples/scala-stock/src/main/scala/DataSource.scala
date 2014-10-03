package io.prediction.examples.stock

import io.prediction.controller.Params
import io.prediction.controller.PDataSource
import io.prediction.controller.LDataSource
import io.prediction.controller.EmptyParams

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.broadcast.Broadcast

import io.prediction.data.storage.Storage
import io.prediction.data.storage.{ ItemTrend, ItemTrends }

import com.mongodb.casbah.Imports._
import org.saddle._
import org.saddle.index.IndexTime
import com.github.nscala_time.time.Imports._


/** Primary parameter for [[[DataSource]]].
  *
  * @param baseDate identify the beginning of our global time window, and
  * the rest are use index.
  * @param fromIdx the first date for testing
  * @param untilIdx the last date (exclusive) for testing
  * @param trainingWindowSize number of days used for training
  * @param testingWindowSize  number of days for each testing data
  *
  * [[[DataSource]]] chops data into (overlapping) multiple
  * pieces. Within each piece, it is further splitted into training and testing
  * set. The testing sets is from <code>fromIdx</code> until
  * <code>untilIdx</code> with a step size of <code>testingWindowSize</code>.
  * A concrete example: (from, until, windowSize) = (100, 150, 20), it generates
  * three testing sets corresponding to time range: [100, 120), [120, 140), [140, 150).
  * For each testing sets, it also generates the training data set using
  * <code>maxTrainingWindowSize</code>. Suppose trainingWindowSize = 50 and testing set =
  * [100, 120), the training set draws data in time range [50, 100).
  */

case class DataSourceParams(
  val appid: Int = 1008,
  val baseDate: DateTime,
  val fromIdx: Int,
  val untilIdx: Int,
  val trainingWindowSize: Int,
  val maxTestingWindowSize: Int,
  val marketTicker: String,
  val tickerList: Seq[String]) extends Params {}


class DataSource(val dsp: DataSourceParams)
  extends PDataSource[
      DataSourceParams,
      DataParams,
      RDD[TrainingData],
      QueryDate,
      AnyRef] {
  @transient lazy val itemTrendsDbGetTicker =
    Storage.getAppdataItemTrends().get(1008, _: String).get
  @transient lazy val itemTrendsDb = Storage.getAppdataItemTrends()

  def read(sc: SparkContext)
  : Seq[(DataParams, RDD[TrainingData], RDD[(QueryDate, AnyRef)])] = {
    val rawData = readRawData()

    // Broadcast it.
    val rawDataB = sc.broadcast(rawData)

    val dataParams = DataParams(rawDataB)

    val dataSet: Seq[(TrainingData, Seq[(QueryDate, AnyRef)])] =
      Range(dsp.fromIdx, dsp.untilIdx, dsp.maxTestingWindowSize).map { idx => {
        val trainingData = TrainingData(
          untilIdx = idx,
          maxWindowSize = dsp.trainingWindowSize,
          rawDataB = rawDataB)

        // cannot evaluate the last item as data view only last until untilIdx.
        val testingUntilIdx = math.min(
          idx + dsp.maxTestingWindowSize,
          dsp.untilIdx - 1)

        val queries = (idx until testingUntilIdx)
          .map { idx => (QueryDate(idx), None) }
        (trainingData, queries)
      }}

    dataSet.map { case (trainingData, queries) =>
      (dataParams,
        sc.parallelize(Array(trainingData)),
        sc.parallelize(queries))
    }
  }

  def getPriceSeriesFromItemTrend(timeIndex: IndexTime, itemTrend: ItemTrend)
  : (Series[DateTime, Double], Series[DateTime, Boolean]) = {
    // The current implementation imports the whole series and reindex with the
    // input timeIndex. Of course not the most efficient one. May revisit later.
    val daily = itemTrend.daily

    val timestamps: IndexTime = IndexTime(daily.map(_._1): _*)

    val aprice = daily.map(_._7).toArray
    val active = daily.map(_._8).toArray

    val apriceSeries: Series[DateTime, Double] =
      Series(Vec(aprice), timestamps).reindex(timeIndex)
    val activeSeries = Series(Vec(active), timestamps).reindex(timeIndex)

    (apriceSeries, activeSeries)
  }

  def readRawData(): RawData = {
    val allTickers = dsp.tickerList :+ dsp.marketTicker

    val tickerTrends: Map[String, ItemTrend] = itemTrendsDb
      .getByIds(dsp.appid, allTickers)
      .map { e => (e.id, e) }
      .toMap

    val market = tickerTrends(dsp.marketTicker)
    val timestampArray = market.daily
      .map(_._1)
      .distinct
      .filter(dsp.baseDate <= _)
      .sorted
      .take(dsp.untilIdx)

    val timeIndex: IndexTime = IndexTime(timestampArray: _*)

    val tickerData
    : Map[String, (Series[DateTime, Double], Series[DateTime, Boolean])] =
    tickerTrends.mapValues { e => getPriceSeriesFromItemTrend(timeIndex, e) }

    val price = tickerData.mapValues { s => s._1.toVec.contents }.toArray
    val active = tickerData.mapValues { s => s._2.toVec.contents }.toArray

    val rawData = new RawData(
      tickers = allTickers.toArray,
      mktTicker = dsp.marketTicker,
      timeIndex = timestampArray.toArray,
      _price = price,
      _active = active)

    rawData
  }
}
