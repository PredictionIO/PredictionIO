package io.prediction.examples.stock

// YahooDataSource reads PredictionIO event store directly.

import io.prediction.data.storage.Event
import io.prediction.data.storage.Events
import io.prediction.data.storage.Storage
import io.prediction.data.view.LBatchView
import io.prediction.data.storage.DataMap

import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import com.github.nscala_time.time.Imports._

import scala.collection.mutable.{ Map => MMap }
import scala.collection.GenMap

import io.prediction.controller._
import io.prediction.controller.{ Params => BaseParams }


import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.broadcast.Broadcast

import org.json4s._
//import org.saddle._

case class HistoricalData(
  val ticker: String,
  val timeIndex: Array[DateTime],
  val close: Array[Double],
  val adjClose: Array[Double],
  val adjReturn: Array[Double],
  val volume: Array[Double],
  val active: Array[Boolean]) extends Serializable {

  override def toString(): String = {
    s"HistoricalData($ticker, ${timeIndex.head}, ${timeIndex.last}, " +
      s"${close.last})"
  }

  def toDetailedString(): String = {
    val adjCloseStr = adjClose.mkString("[", ", ", "]")
    val adjReturnStr = adjReturn.mkString("[", ", ", "]")
    val activeStr = active.mkString("[", ", ", "]")

    (s"HistoricalData($ticker, ${timeIndex.head}, ${timeIndex.last}, \n" + 
      s"  adjClose=$adjCloseStr\n" + 
      s"  adjReturn=$adjReturnStr\n" + 
      s"  active=$activeStr)")
  }
}

object HistoricalData {
  def apply(ticker: String, timeIndex: Array[DateTime]): HistoricalData = {
    val n = timeIndex.size
    HistoricalData(
      ticker,
      timeIndex,
      close = Array.fill(n)(0.0),
      adjClose = Array.fill(n)(0.0),
      adjReturn = Array.fill(n)(0.0),
      volume = Array.fill(n)(0.0),
      active = Array.fill(n)(false))
  }
}


class YahooDataSource(val params: YahooDataSource.Params) 
  extends PDataSource[
      YahooDataSource.Params,
      DataParams,
      RDD[TrainingData],
      QueryDate,
      AnyRef] {
  @transient lazy val batchView = new LBatchView(
    params.appId, params.startTime, params.untilTime)
  
  val timezone = DateTimeZone.forID("US/Eastern")
  val windowParams = params.windowParams
  val marketTicker = windowParams.marketTicker

  @transient lazy val market: HistoricalData = getTimeIndex()
  @transient lazy val timeIndex: Array[DateTime] = market.timeIndex
  @transient lazy val timeIndexSet: Set[DateTime] = timeIndex.toSet
  
  def merge(intermediate: YahooDataSource.Intermediate, e: Event,
    timeIndexSetOpt: Option[Set[DateTime]]) 
  : YahooDataSource.Intermediate = {
    val dm: DataMap = e.properties     

    // TODO: Check ticker in intermediate
    
    val yahooData = dm.get[JObject]("yahoo")

    // used by json4s "extract" method.
    implicit val formats = DefaultFormats

    val closeList = (yahooData \ "close").extract[Array[Double]]
    val adjCloseList = (yahooData \ "adjclose").extract[Array[Double]]
    val volumeList = (yahooData \ "volume").extract[Array[Double]]

    val tList: Array[DateTime] = (yahooData \ "t").extract[Array[Long]]
      .map(t => new DateTime(t * 1000, timezone))

    // Add data either 
    // 1. timeIndex exists and t is in timeIndex, or 
    // 2. timeIndex is None.
    val newDailyMap: Map[DateTime, YahooDataSource.Daily] = 
      tList.zipWithIndex.drop(1)
      .filter { case (t, idx) => timeIndexSetOpt.map(_(t)).getOrElse(true) }
      .map { case (t, idx) =>
        val adjReturn = (adjCloseList(idx) / adjCloseList(idx - 1)) - 1

        val daily = YahooDataSource.Daily(
          close = closeList(idx),
          adjClose = adjCloseList(idx),
          adjReturn = adjReturn,
          volume = volumeList(idx),
          active = true,
          prevDate = tList(idx - 1))

        (t -> daily)
      }
      .toMap

    YahooDataSource.Intermediate(
      ticker = e.entityId, 
      dailyMap = intermediate.dailyMap ++ newDailyMap)
  }

  def mergeTimeIndex(intermediate: YahooDataSource.Intermediate, e: Event)
  : YahooDataSource.Intermediate = merge(intermediate, e, None)

  def mergeStock(intermediate: YahooDataSource.Intermediate, e: Event)
  : YahooDataSource.Intermediate = merge(intermediate, e, Some(timeIndexSet))

  def finalizeTimeIndex(intermediate: YahooDataSource.Intermediate)
  : HistoricalData = {
    val dailyMap = intermediate.dailyMap
    val ticker = intermediate.ticker

    // Construct the time index with windowParams
    val timeIndex: Array[DateTime] = dailyMap.keys.toArray
      .filter(_.isAfter(params.windowParams.baseDate))
      .sortBy(identity)
      .take(params.windowParams.untilIdx)

    // Check if the time is continuous
    (1 until timeIndex.size).foreach { idx => {
      require(dailyMap(timeIndex(idx)).prevDate == timeIndex(idx - 1),
        s"Time must be continuous. " +
        s"For ticker $ticker, there is a gap between " +
        s"${timeIndex(idx - 1)} and ${timeIndex(idx)}. " + 
        s"Please import data to cover the gap or use a shorter range.")
    }}

    val adjReturn = timeIndex.map(t => dailyMap(t).adjReturn)

    HistoricalData(
      ticker = ticker, 
      timeIndex = timeIndex,
      close = timeIndex.map(t => dailyMap(t).close),
      adjClose = return2Close(adjReturn),
      adjReturn = adjReturn,
      volume = timeIndex.map(t => dailyMap(t).volume),
      active = Array.fill(timeIndex.size)(true))
  }

  def return2Close(returns: Array[Double], base: Double = 100.0)
  : Array[Double] = {
    var v = base
    returns.map { ret =>
      v *= (1 + ret)
      v
    }
  }

  // Traverse the timeIndex to construct the actual time series using dailyMap
  // and extra fillNA logic.
  //
  // The time series is constructed in the same order as the global timeIndex
  // array. For a datetime t, if dailyMap contains the data, it calls valueFunc
  // to extract the value; otherwise, it calls fillNaFunc with the optional last
  // extracted value and get the default value.
  def activeFilter[A : Manifest](
    dailyMap: GenMap[DateTime, YahooDataSource.Daily], 
    valueFunc: YahooDataSource.Daily => A, 
    fillNAFunc: Option[A] => A) : Array[A] = {

    var lastOpt: Option[A] = None
    
    timeIndex
    .map { t => 
      if (dailyMap.contains(t)) {
        val v = valueFunc(dailyMap(t)) 
        lastOpt = Some(v)
        v
      } else {
        fillNAFunc(lastOpt)
      }
    }
    .toArray
  }

  def finalizeStock(intermediate: YahooDataSource.Intermediate)
  : HistoricalData = {
    val dailyMap = intermediate.dailyMap 

    val adjReturn = activeFilter[Double](dailyMap, _.adjReturn, _ => 0.0)

    HistoricalData(
      ticker = intermediate.ticker,
      timeIndex = timeIndex,
      close = activeFilter[Double](dailyMap, _.close, _.getOrElse(0.0)),
      adjClose = return2Close(adjReturn),
      adjReturn = adjReturn,
      volume = activeFilter[Double](dailyMap, _.adjReturn, _ => 0.0),
      active = activeFilter[Boolean](dailyMap, _.active, _ => false))
  }

  def getTimeIndex(): HistoricalData = {
    // Only extracts market ticker as the main reference of market hours
    val predicate = (e: Event) => 
      (e.entityType == params.entityType && e.entityId == marketTicker)
    
    val tickerMap: Map[String, HistoricalData] = batchView
      .aggregateByEntityOrdered(
        predicate,
        YahooDataSource.Intermediate(),
        mergeTimeIndex)
      .mapValues(finalizeTimeIndex)
      
    tickerMap(marketTicker)
  }

  def getHistoricalDataSet()
  : Map[String, HistoricalData] = {
    // Also extract market ticker again, just as a normal stock.
    val tickerSet = (windowParams.tickerList :+ windowParams.marketTicker).toSet
    val predicate = (e: Event) => 
      (e.entityType == params.entityType && tickerSet(e.entityId))

    val defaultTickerMap: Map[String, HistoricalData] =
      params.windowParams.tickerList.map { 
        ticker => (ticker -> HistoricalData(ticker, timeIndex))
      }
      .toMap

    val tickerMap: Map[String, HistoricalData] = batchView
      .aggregateByEntityOrdered(
        predicate,
        YahooDataSource.Intermediate(),
        mergeStock)
      .mapValues(finalizeStock)

    /*
    tickerMap.map { case (ticker, data) => 
      println(ticker)
      println(data.toDetailedString)
    }
    */

    defaultTickerMap ++ tickerMap
  }

  def getRawData(tickerMap: Map[String, HistoricalData]): RawData = {
    val allTickers = windowParams.tickerList :+ windowParams.marketTicker
    
    val price: Array[(String, Array[Double])] = allTickers
      .map { ticker => (ticker, tickerMap(ticker).adjClose) }
      .toArray

    val active: Array[(String, Array[Boolean])] = allTickers
      .map { ticker => (ticker, tickerMap(ticker).active) }
      .toArray

    new RawData(
      tickers = windowParams.tickerList.toArray,
      mktTicker = windowParams.marketTicker,
      timeIndex = timeIndex,
      _price = price,
      _active = active)
  }

  def read(sc: SparkContext)
  : Seq[(DataParams, RDD[TrainingData], RDD[(QueryDate, AnyRef)])] = {
    val historicalSet = getHistoricalDataSet()
    //data.foreach { println }
    val rawData = getRawData(historicalSet)
    //println(rawData)
    
    // Broadcast it.
    val rawDataB = sc.broadcast(rawData)

    val dataParams = DataParams(rawDataB)

    val dsp: DataSourceParams = windowParams

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
}

object YahooDataSource {
  case class Params(
    val windowParams: DataSourceParams,
    // Below filters with DataAPISpecific details
    val appId: Int,  // Ignore appId in DataSourceParams
    val entityType: String,
    val startTime: Option[DateTime] = None,
    val untilTime: Option[DateTime] = None
  ) extends BaseParams

  case class Daily(
    val close: Double,
    val adjClose: Double,
    val adjReturn: Double,
    val volume: Double,
    val active: Boolean,
    // prevDate is used to verify continuity
    val prevDate: DateTime)

  /** Intermediate storage for constructing historical data
    * @param timeIndexSet Only datetime in this set is used to create historical
    * data.
    */
  case class Intermediate(
    val ticker: String = "", 
    val dailyMap: Map[DateTime, Daily] = Map[DateTime, Daily]()
    ) extends Serializable {
    override def toString(): String = 
      s"YDS.Intermediate($ticker, size=${dailyMap.size})"
  }


  /*
  def main(args: Array[String]) {
    val params = Params(
      appId = 1,
      untilTime = Some(new DateTime(2014, 5, 1, 0, 0)))
      //untilTime = None)

    val ds = new YahooDataSource(params)
    //ds.read
  }
  */
}

object PredefinedDSP {
  val BigSP500 = YahooDataSource.Params(
    appId = 2,
    entityType = "yahoo",
    untilTime = None,
    windowParams = DataSourceParams(
      baseDate = new DateTime(2000, 1, 1, 0, 0),
      fromIdx = 250,
      untilIdx = 3500,
      trainingWindowSize = 200,
      maxTestingWindowSize = 30,
      marketTicker = "SPY",
      tickerList = Run.sp500List))

  val SmallSP500 = YahooDataSource.Params(
    appId = 2,
    entityType = "yahoo",
    untilTime = None,
    windowParams = DataSourceParams(
      baseDate = new DateTime(2000, 1, 1, 0, 0),
      fromIdx = 250,
      untilIdx = 3500,
      trainingWindowSize = 200,
      maxTestingWindowSize = 30,
      marketTicker = "SPY",
      tickerList = Run.sp500List.take(25)))

  val Test = YahooDataSource.Params(
    appId = 1,
    entityType = "yahoo",
    untilTime = Some(new DateTime(2014, 5, 1, 0, 0)),
    windowParams = DataSourceParams(
      baseDate = new DateTime(2014, 1, 1, 0, 0),
      fromIdx = 20,
      untilIdx = 50,
      trainingWindowSize = 15,
      maxTestingWindowSize = 10,
      marketTicker = "SPY",
      tickerList = Seq("AAPL", "MSFT", "IBM", "FB", "AMZN", "IRONMAN")))
}

object YahooDataSourceRun {

  def main(args: Array[String]) {
    // Make sure you have a lot of memory.
    // --driver-memory 12G

    val dsp = PredefinedDSP.BigSP500
    //val dsp = PredefinedDSP.SmallSP500
    //val dsp = PredefinedDSP.Test
    
    val momentumParams = MomentumStrategyParams(20, 3)

    //val x =  Series(Vec(1,2,3))
    //println(x)

    val metricsParams = BacktestingParams(
      enterThreshold = 0.01,
      exitThreshold = 0.0,
      maxPositions = 10//,
      //optOutputPath = Some(new File("metrics_results").getCanonicalPath)
    )

    Workflow.run(
      dataSourceClassOpt = Some(classOf[YahooDataSource]),
      dataSourceParams = dsp,
      preparatorClassOpt = Some(IdentityPreparator(classOf[DataSource])),
      algorithmClassMapOpt = Some(Map(
        //"" -> classOf[MomentumStrategy]
        "" -> classOf[RegressionStrategy]
      )),
      //algorithmParamsList = Seq(("", momentumParams)),
      algorithmParamsList = Seq(("", EmptyParams())),
      servingClassOpt = Some(FirstServing(classOf[EmptyStrategy])),
      metricsClassOpt = Some(classOf[BacktestingMetrics]),
      metricsParams = metricsParams,
      params = WorkflowParams(
        verbose = 0,
        saveModel = false,
        batch = "Imagine: Stock III"))
  }
}




