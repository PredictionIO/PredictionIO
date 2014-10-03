package myengine

import io.prediction.BaseServerParams
import io.prediction.Server

class MyServerParams() extends BaseServerParams {}

class MyServer extends Server[Feature, Prediction, MyServerParams] {

  def combine(feature: Feature, predictions: Seq[Prediction]): Prediction =
    new Prediction()

}
