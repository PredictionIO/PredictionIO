package org.apache.predictionio.examples.mlc

import org.apache.predictionio.engines.itemrank.PreparatorParams
import org.apache.predictionio.engines.itemrank.EventsDataSourceParams
import org.apache.predictionio.engines.itemrank.ItemRankEngine
import org.apache.predictionio.engines.itemrank.ItemRankDetailedEvaluator
import org.apache.predictionio.engines.itemrank.DetailedEvaluatorParams
import org.apache.predictionio.engines.itemrank.MeasureType
import org.apache.predictionio.engines.itemrank.mahout.ItemBasedAlgoParams
import org.apache.predictionio.engines.base.AttributeNames
import org.apache.predictionio.engines.base.EventsSlidingEvalParams
import org.apache.predictionio.engines.base.BinaryRatingParams
import org.apache.predictionio.controller.WorkflowParams
import org.apache.predictionio.controller.Workflow
import org.apache.predictionio.controller.EngineParams

import com.github.nscala_time.time.Imports._

object CommonParams {
  val DataSourceAttributeNames = AttributeNames(
    user = "pio_user",
    item = "pio_item",
    u2iActions = Set("rate"),
    itypes = "pio_itypes",
    starttime = "pio_starttime",
    endtime = "pio_endtime",
    inactive = "pio_inactive",
    rating = "pio_rating")

  val PreparatorParams = new PreparatorParams(
    actions = Map("rate" -> None),
    conflict = "latest")
    
  val MahoutAlgoParams0 = new ItemBasedAlgoParams(
    booleanData = true,
    itemSimilarity = "LogLikelihoodSimilarity",
    weighted = false,
    nearestN = 10,
    threshold = 4.9E-324,
    numSimilarItems = 50,
    numUserActions = 50,
    freshness = 0,
    freshnessTimeUnit = 86400,
    recommendationTime = Some(DateTime.now.millis))

  val CompleteDataSourceParams = EventsDataSourceParams(
    appId = 9,
    actions = Set("rate"),
    attributeNames = CommonParams.DataSourceAttributeNames,
    slidingEval = Some(new EventsSlidingEvalParams(
      firstTrainingUntilTime = new DateTime(1998, 2, 1, 0, 0),
      evalDuration = Duration.standardDays(7),
      evalCount = 12)))
}

object Evaluation1 {
  def main(args: Array[String]) {
    // Engine Settings
    val engine = ItemRankEngine() 

    val dsp = EventsDataSourceParams(
      appId = 9,
      actions = Set("rate"),
      attributeNames = CommonParams.DataSourceAttributeNames,
      slidingEval = Some(new EventsSlidingEvalParams(
        firstTrainingUntilTime = new DateTime(1998, 2, 1, 0, 0),
        evalDuration = Duration.standardDays(7),
        evalCount = 3))
    )

    val engineParams = new EngineParams(
      dataSourceParams = dsp,
      preparatorParams = CommonParams.PreparatorParams,
      algorithmParamsList = Seq(
        ("mahoutItemBased", CommonParams.MahoutAlgoParams0))
    )

    // Evaluator Setting
    val evaluatorParams = new DetailedEvaluatorParams(
      ratingParams = new BinaryRatingParams(
        actionsMap = Map("rate" -> None),
        goodThreshold = 3),
      measureType = MeasureType.PrecisionAtK,
      measureK = 10
    ) 

    // Run
    Workflow.runEngine(
      params = WorkflowParams(batch = "MLC: Evaluation1"),
      engine = engine,
      engineParams = engineParams,
      evaluatorClassOpt = Some(classOf[ItemRankDetailedEvaluator]),
      evaluatorParams = evaluatorParams
    )
  }
}

object Evaluation2 {
  def main(args: Array[String]) {
    // Engine Settings
    val engine = ItemRankEngine() 

    val engineParams = new EngineParams(
      dataSourceParams = CommonParams.CompleteDataSourceParams,
      preparatorParams = CommonParams.PreparatorParams,
      algorithmParamsList = Seq(
        ("mahoutItemBased", CommonParams.MahoutAlgoParams0))
    )

    // Evaluator Setting
    val evaluatorParams = new DetailedEvaluatorParams(
      ratingParams = new BinaryRatingParams(
        actionsMap = Map("rate" -> None),
        goodThreshold = 3),
      measureType = MeasureType.PrecisionAtK,
      measureK = 10
    ) 

    // Run
    Workflow.runEngine(
      params = WorkflowParams(batch = "MLC: Evaluation2"),
      engine = engine,
      engineParams = engineParams,
      evaluatorClassOpt = Some(classOf[ItemRankDetailedEvaluator]),
      evaluatorParams = evaluatorParams
    )
  }
}
