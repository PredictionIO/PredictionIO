package org.template.recommendation

import org.apache.predictionio.controller.LServing

import scala.io.Source

import org.apache.predictionio.controller.Params  // ADDED

// ADDED ServingParams to specify the blacklisting file location.
case class ServingParams(filepath: String) extends Params

class Serving(val params: ServingParams)
  extends LServing[Query, PredictedResult] {

  override
  def serve(query: Query, predictedResults: Seq[PredictedResult])
  : PredictedResult = {
    val disabledProducts: Set[String] = Source
      .fromFile(params.filepath)
      .getLines
      .toSet

    val itemScores = predictedResults.head.itemScores
    PredictedResult(itemScores.filter(ps => !disabledProducts(ps.item)))
  }
}
