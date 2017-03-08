/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sample.helloworld

import org.apache.predictionio.controller._

import scala.io.Source
import scala.collection.immutable.HashMap

// all data need to be serializable
class MyTrainingData(
  // list of (day, temperature) tuples
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

class MyPredictedResult(
  val temperature: Double
) extends Serializable

case class MyDataSourceParams(val multiplier: Int
                             ) extends Params

class MyDataSource extends LDataSource[
  MyTrainingData,
  EmptyEvaluationInfo,
  MyQuery,
  EmptyActualResult] {

  /* override this to return Training Data only */

  override
  def readTraining(): MyTrainingData = {
    val lines = Source.fromFile("../data/helloworld/data.csv").getLines()
      .toList.map{ line =>
        val data = line.split(",")
        (data(0), data(1).toDouble)
      }

    new MyTrainingData(lines)
  }
}

class MyAlgorithm extends LAlgorithm[
  MyTrainingData,
  MyModel,
  MyQuery,
  MyPredictedResult] {


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
  def predict(model: MyModel, query: MyQuery): MyPredictedResult = {
    val temp = model.temperatures(query.day)
    new MyPredictedResult(temp)
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
