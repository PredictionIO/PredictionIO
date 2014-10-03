package org.sample.test

import io.prediction.controller._

import scala.io.Source
import scala.collection.immutable.HashMap
import io.prediction.data.view.LBatchView

// all data need to be serializable
case class MyTrainingData(
  // list of (day, temperature) tuples
  val data: List[String],
  val temperatures: List[(String, Double)]
) extends Serializable

class MyQuery(
  val day: String
) extends Serializable

class MyModel(
  val temperatures: HashMap[String, Double]
) extends Serializable {
  override def toString = temperatures.toString
}

class MyPrediction(
  val temperature: Double
) extends Serializable

// controller components
class MyDataSource extends LDataSource[EmptyDataSourceParams, EmptyDataParams,
  MyTrainingData, MyQuery, EmptyActual] {

  @transient lazy val batchView = new LBatchView(3,
    None, None)

  /* override this to return Training Data only */
  override
  def readTraining(): MyTrainingData = {
    val lines = Source.fromFile("../data/helloworld/data.csv").getLines()
      .toList.map{ line =>
        val data = line.split(",")
        (data(0), data(1).toDouble)
      }

    new MyTrainingData(
      data = batchView.events.map(_.event),
      lines)
  }
}

class MyAlgorithm extends LAlgorithm[EmptyAlgorithmParams, MyTrainingData,
  MyModel, MyQuery, MyPrediction] {

  override
  def train(pd: MyTrainingData): MyModel = {
    // calculate average value of each day
    val average = pd.temperatures
      .groupBy(_._1) // group by day
      .mapValues{ list =>
        val tempList = list.map(_._2) // get the temperature
        tempList.sum / tempList.size
      }

    // trait Map is not serializable, use concrete class HashMap
    new MyModel(HashMap[String, Double]() ++ average)
  }

  override
  def predict(model: MyModel, query: MyQuery): MyPrediction = {
    val temp = model.temperatures(query.day)
    new MyPrediction(temp)
  }
}

// factory
object MyEngineFactory extends IEngineFactory {
  override
  def apply() = {
    /* SimpleEngine only requires one DataSouce and one Algorithm */
    new SimpleEngine(
      classOf[MyDataSource],
      classOf[MyAlgorithm]
    )
  }
}
