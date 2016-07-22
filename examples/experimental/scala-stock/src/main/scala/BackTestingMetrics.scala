package org.apache.predictionio.examples.stock

import org.apache.predictionio.controller.Params
import org.apache.predictionio.controller.Evaluator
import org.apache.predictionio.controller.NiceRendering
import com.github.nscala_time.time.Imports._
import scala.collection.mutable.{ Map => MMap, ArrayBuffer }

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
//import org.json4s.native.Serialization.{read, write}

import org.apache.predictionio.engines.util.{ EvaluatorVisualization => MV }

import breeze.stats.{ mean, meanAndVariance, MeanAndVariance }

case class BacktestingParams(
  val enterThreshold: Double,
  val exitThreshold: Double,
  val maxPositions: Int = 1,
  val optOutputPath: Option[String] = None
) extends Params {}

// prediction is Ticker -> ({1:Enter, -1:Exit}, ActualReturn)
class DailyResult(
  //val date: DateTime,
  val dateIdx: Int,
  val toEnter: Seq[String],
  val toExit: Seq[String])
extends Serializable {}

case class DailyStat (
  time: Long,
  nav: Double,
  ret: Double,
  market: Double,
  positionCount: Int
)

case class OverallStat (
  ret: Double,
  vol: Double,
  sharpe: Double,
  days: Int
)

case class BacktestingResult(
  daily: Seq[DailyStat],
  overall: OverallStat
) with NiceRendering {
  override def toString(): String = overall.toString

  def toHTML(): String = {
    implicit val formats = DefaultFormats
    html.backtesting().toString
  }

  def toJSON(): String = {
    implicit val formats = DefaultFormats
    Serialization.write(this)
  }
}

class BacktestingEvaluator(val params: BacktestingParams)
  extends Evaluator[
      DataParams, QueryDate, Prediction, AnyRef,
      DailyResult, Seq[DailyResult], BacktestingResult] {

  def evaluateUnit(queryDate: QueryDate, prediction: Prediction,
    unusedActual: AnyRef)
    : DailyResult = {

    val todayIdx = queryDate.idx

    // Decide enter / exit, also sort by pValue desc
    val data = prediction.data
    .map { case (ticker, pValue) => {
      val dir = pValue match {
        case p if p >= params.enterThreshold => 1
        case p if p <= params.exitThreshold => -1
        case _ => 0
      }
      (ticker, dir, pValue)
    }}
    .toArray
    .sortBy(-_._3)

    val toEnter = data.filter(_._2 == 1).map(_._1)
    val toExit = data.filter(_._2 == -1).map(_._1)

    new DailyResult(
      dateIdx = todayIdx,
      toEnter = toEnter,
      toExit = toExit)
  }

  def evaluateSet(dp: DataParams, input: Seq[DailyResult])
    : Seq[DailyResult] = input

  def evaluateAll(input: Seq[(DataParams, Seq[DailyResult])])
  : BacktestingResult = {
    var dailyResultsSeq = input
      .map(_._2)
      .flatten
      .toArray
      .sortBy(_.dateIdx)

    var rawData = input.head._1.rawDataB.value
    val retFrame = rawData._retFrame
    val priceFrame = rawData._priceFrame
    val mktTicker = rawData.mktTicker

    val dailyNavs = ArrayBuffer[Double]()

    val dailyStats = ArrayBuffer[DailyStat]()

    val initCash = 1000000.0
    var cash = initCash
    // Ticker to current size
    val positions = MMap[String, Double]()
    val maxPositions = params.maxPositions

    for (daily <- dailyResultsSeq) {
      val todayIdx = daily.dateIdx
      val today = rawData.timeIndex(todayIdx)
      val todayRet = retFrame.rowAt(todayIdx)
      val todayPrice = priceFrame.rowAt(todayIdx)

      // Update price change
      positions.keys.foreach { ticker => {
        positions(ticker) *= todayRet.first(ticker).get
      }}

      // Determine exit
      daily.toExit.foreach { ticker => {
        if (positions.contains(ticker)) {
          val money = positions.remove(ticker).get
          cash += money
        }
      }}

      // Determine enter
      val slack = maxPositions - positions.size
      val money = cash / slack
      daily.toEnter
      .filter(t => !positions.contains(t))
      .take(slack)
      .map{ ticker => {
        cash -= money
        positions += (ticker -> money)
      }}

      // Book keeping
      val nav = cash + positions.values.sum

      val ret = (if (dailyStats.isEmpty) 0 else {
        val yestStats = dailyStats.last
        val yestNav = yestStats.nav
        (nav - yestNav) / nav - 1
        })

      dailyStats.append(DailyStat(
        time = today.getMillis(),
        nav = nav,
        ret = ret,
        market = todayPrice.first(mktTicker).get,
        positionCount = positions.size
      ))
    }
    // FIXME. Force Close the last day.

    val lastStat = dailyStats.last

    //val dailyVariance = meanAndVariance(dailyStats.map(_.ret))._2
    //val dailyVariance = meanAndVariance(dailyStats.map(_.ret))._2
    val retStats: MeanAndVariance = meanAndVariance(dailyStats.map(_.ret))
    //val dailyVol = math.sqrt(dailyVariance)
    //val annualVol = dailyVariance * math.sqrt(252.0)
    val annualVol = retStats.stdDev * math.sqrt(252.0)
    val n = dailyStats.size
    val totalReturn = lastStat.nav / initCash

    val annualReturn = math.pow(totalReturn, 252.0 / n) - 1
    val sharpe = annualReturn / annualVol

    val overall = OverallStat(
      annualReturn,
      annualVol,
      sharpe,
      n)

    val result = BacktestingResult(
      daily = dailyStats,
      overall = overall
    )

    params.optOutputPath.map { path => MV.save(result, path) }

    result
  }
}

object RenderMain {
  def main(args: Array[String]) {
    MV.render(MV.load[BacktestingResult](args(0)), args(0))
  }
}
