package org.template.recommendation

import org.apache.predictionio.controller.LServing

class Serving
  extends LServing[Query, PredictedResult] {

  override def serve(query: Query,
    predictedResults: Seq[PredictedResult]): PredictedResult = {
    predictedResults.head
  }
}
