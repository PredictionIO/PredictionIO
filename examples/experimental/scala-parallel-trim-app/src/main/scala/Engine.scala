package org.apache.predictionio.examples.experimental.trimapp

import org.apache.predictionio.controller.IEngineFactory
import org.apache.predictionio.controller.Engine
import org.apache.predictionio.controller._

case class Query(q: String) extends Serializable

case class PredictedResult(p: String) extends Serializable

object VanillaEngine extends IEngineFactory {
  def apply() = {
    new Engine(
      classOf[DataSource],
      PIdentityPreparator(classOf[DataSource]),
      Map("" -> classOf[Algorithm]),
      classOf[Serving])
  }
}
