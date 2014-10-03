package myengine

import io.prediction.{ Algorithm, BaseAlgoParams }

class MyAlgoParams() extends BaseAlgoParams {}

class MyAlgo
  extends Algorithm[TrainingData, Feature, Prediction, Model, BaseAlgoParams] {

  override def init(algoParams: BaseAlgoParams): Unit = {}

  def train(trainingData: TrainingData): Model = {
    new Model()
  }

  def predict(model: Model, feature: Feature): Prediction = {
    new Prediction()
  }

}
