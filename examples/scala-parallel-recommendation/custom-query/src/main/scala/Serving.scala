package org.template.recommendation

import org.apache.predictionio.controller.LServing

class Serving extends LServing[Query, PredictedResult] {

  override def serve(query: Query,
            predictedResults: Seq[PredictedResult]): PredictedResult =
    predictedResults.headOption.map { result ⇒
      val preparedItems = result.itemScores
        .sortBy { case ItemScore(item, score, year) ⇒ year }(
          Ordering.Option[Int].reverse)
      new PredictedResult(preparedItems)
    }.getOrElse(new PredictedResult(Array.empty[ItemScore]))
}
