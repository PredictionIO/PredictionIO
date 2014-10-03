package myengine

import io.prediction.{
  BaseEvaluationParams,
  BaseTrainingDataParams,
  BaseEvaluationDataParams,
  BaseTrainingData,
  BaseFeature,
  BasePrediction,
  BaseActual,
  BaseModel,
  BaseEvaluationUnit,
  BaseEvaluationResults
}

class EvaluationParams() extends BaseEvaluationParams {}

class TrainingDataParams() extends BaseTrainingDataParams {}

class EvaluationDataParams() extends BaseEvaluationDataParams {}

class TrainingData() extends BaseTrainingData {}

class Model() extends BaseModel {}

class Feature() extends BaseFeature {}

class Prediction() extends BasePrediction {}

class Actual() extends BaseActual {}

class EvaluationUnit() extends BaseEvaluationUnit {}

class EvaluationResults extends BaseEvaluationResults {}
