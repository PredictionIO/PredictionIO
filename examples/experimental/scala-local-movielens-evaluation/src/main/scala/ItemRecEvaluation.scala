package org.apache.predictionio.examples.mlc

import org.apache.predictionio.engines.itemrec.ItemRecEngine
import org.apache.predictionio.engines.itemrec.EventsDataSourceParams
import org.apache.predictionio.engines.itemrec.PreparatorParams
import org.apache.predictionio.engines.itemrec.NCItemBasedAlgorithmParams
import org.apache.predictionio.engines.itemrec.EvalParams
import org.apache.predictionio.engines.itemrec.ItemRecEvaluator
import org.apache.predictionio.engines.itemrec.ItemRecEvaluatorParams
import org.apache.predictionio.engines.itemrec.MeasureType
import org.apache.predictionio.engines.base.EventsSlidingEvalParams
import org.apache.predictionio.engines.base.BinaryRatingParams

import org.apache.predictionio.controller.EngineParams
import org.apache.predictionio.controller.Workflow
import org.apache.predictionio.controller.WorkflowParams

import com.github.nscala_time.time.Imports._

// Recommend to run with "--driver-memory 2G"
object ItemRecEvaluation1 {
  def main(args: Array[String]) {
    val engine = ItemRecEngine()
    
    val dsp = EventsDataSourceParams(
      appId = 9,
      actions = Set("rate"),
      attributeNames = CommonParams.DataSourceAttributeNames,
      slidingEval = Some(new EventsSlidingEvalParams(
        firstTrainingUntilTime = new DateTime(1998, 2, 1, 0, 0),
        evalDuration = Duration.standardDays(7),
        evalCount = 12)),
        //evalCount = 3)),
      evalParams = Some(new EvalParams(queryN = 10))
    )
  
    val pp = new PreparatorParams(
      actions = Map("rate" -> None),
      seenActions = Set("rate"),
      conflict = "latest")

    val ncMahoutAlgoParams = new NCItemBasedAlgorithmParams(
      booleanData = true,
      itemSimilarity = "LogLikelihoodSimilarity",
      weighted = false,
      threshold = 4.9E-324,
      nearestN = 10,
      unseenOnly = false,
      freshness = 0,
      freshnessTimeUnit = 86400)
    
    val engineParams = new EngineParams(
      dataSourceParams = dsp,
      preparatorParams = pp,
      algorithmParamsList = Seq(
        ("ncMahoutItemBased", ncMahoutAlgoParams)))

    val evaluatorParams = new ItemRecEvaluatorParams(
      ratingParams = new BinaryRatingParams(
        actionsMap = Map("rate" -> None),
        goodThreshold = 3),
      measureType = MeasureType.PrecisionAtK,
      measureK = 10
    ) 
  
    Workflow.runEngine(
      params = WorkflowParams(batch = "MLC: ItemRec Evaluation1", verbose = 0),
      engine = engine,
      engineParams = engineParams,
      evaluatorClassOpt = Some(classOf[ItemRecEvaluator]),
      evaluatorParams = evaluatorParams
    )
  }
}
