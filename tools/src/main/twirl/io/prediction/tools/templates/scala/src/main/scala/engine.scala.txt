package myorg

// Pulls in all PredictionIO controller components
import io.prediction.controller._

// All data classes must be an instance of Serializable
class MyTrainingData(
  val multiplier: Int
) extends Serializable

class MyQuery(
  val multiplicand: Int
) extends Serializable

class MyModel(
  val multiplier: Int
) extends Serializable {
  override def toString = s"MyModel's multiplier: ${multiplier.toString}"
}

class MyPrediction(
  val product: Int
) extends Serializable

case class MyDataSourceParams(
  val multiplier: Int
) extends Params

// Your controller components
class MyDataSource(val dsp: MyDataSourceParams) extends LDataSource[
    MyDataSourceParams,
    EmptyDataParams,
    MyTrainingData,
    MyQuery,
    EmptyActual] {

  /** Implement readTraining() when you do not concern about evaluation.
    *
    */
  override def readTraining(): MyTrainingData = {
    new MyTrainingData(dsp.multiplier)
  }
}

class MyAlgorithm extends LAlgorithm[
    EmptyAlgorithmParams,
    MyTrainingData,
    MyModel,
    MyQuery,
    MyPrediction] {

  override def train(pd: MyTrainingData): MyModel = {
    // Our model is simply one integer...
    new MyModel(pd.multiplier)
  }

  override def predict(model: MyModel, query: MyQuery): MyPrediction = {
    new MyPrediction(query.multiplicand * model.multiplier)
  }
}

/** Engine factory that pieces everything together. SimpleEngine only requires
  * one DataSource and one Algorithm. Preparator is an identity function, and
  * Serving simply outputs Algorithm's prediction without further processing.
  */
object MyEngineFactory extends IEngineFactory {
  override def apply() = {
    new SimpleEngine(
      classOf[MyDataSource],
      classOf[MyAlgorithm])
  }
}
