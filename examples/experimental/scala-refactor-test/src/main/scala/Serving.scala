package pio.refactor

import io.prediction.controller.LServing
import grizzled.slf4j.Logger

class Serving
  extends LServing[Query, PredictedResult] {

  @transient lazy val logger = Logger[this.type]
  override def serve(query: Query,
    predictedResults: Seq[PredictedResult]): PredictedResult = {
    logger.error("Serving.serve")
    predictedResults.head
  }
}
