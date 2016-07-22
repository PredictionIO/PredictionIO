package org.apache.predictionio.examples.pfriendrecommendation

import org.apache.predictionio.controller.LServing
import org.apache.predictionio.controller.EmptyServingParams

class Serving
  extends LServing[Query, Double] {

  override
  def serve(query: Query, predictedResults: Seq[Double]): Double = {
    predictedResults.head
  }
}
