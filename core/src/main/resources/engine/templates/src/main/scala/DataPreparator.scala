package myengine

import io.prediction.DataPreparator
import io.prediction.EvaluationPreparator

class MyPreparator
    extends DataPreparator[TrainingDataParams, TrainingData]
    with EvaluationPreparator[EvaluationDataParams, Feature, Actual] {

  def prepareTraining(params: TrainingDataParams): TrainingData = {
    new TrainingData()
  }

  def prepareEvaluation(params: EvaluationDataParams): Seq[(Feature, Actual)] = {
    Seq[(Feature, Actual)]()
  }
}
