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

package org.apache.predictionio.e2.engine

import java.util.Arrays
import java.util.concurrent.atomic.AtomicReference

import org.apache.predictionio.controller._
import org.apache.spark.SparkContext
import org.apache.spark.ml.PipelineModel
import org.apache.spark.sql.catalyst.expressions.Literal
import org.apache.spark.sql.types.{StructField, StructType}
import org.apache.spark.sql.{Row, SparkSession}


object PythonEngine extends EngineFactory {

  val model = new AtomicReference[PipelineModel]
  private[engine] type Query = Map[String, Any]

  def apply(): Engine[EmptyTrainingData, EmptyEvaluationInfo, EmptyPreparedData,
    Query, Row, EmptyActualResult] = {
    new Engine(
      classOf[PythonDataSource],
      classOf[PythonPreparator],
      Map("default" -> classOf[PythonAlgorithm]),
      classOf[PythonServing])
  }

}

import PythonEngine.Query

class PythonDataSource extends
  PDataSource[EmptyTrainingData, EmptyEvaluationInfo, Query, EmptyActualResult] {
  def readTraining(sc: SparkContext): EmptyTrainingData = new SerializableClass()
}

class PythonPreparator extends PPreparator[EmptyTrainingData, EmptyPreparedData] {
  def prepare(sc: SparkContext, trainingData: EmptyTrainingData): EmptyPreparedData =
    new SerializableClass()
}

class PythonServing extends LServing[Query, Row] {
  def serve(query: Query, predictedResults: Seq[Row]): Row = {
    predictedResults.head
  }
}

class PythonAlgorithm extends
  P2LAlgorithm[EmptyPreparedData, PipelineModel, Query, Row] {

  def train(sc: SparkContext, data: EmptyPreparedData): PipelineModel = {
    PythonEngine.model.get()
  }

  def predict(model: PipelineModel, query: Query): Row = {
    val (colNames, data) = query.toList.unzip

    val rows = Arrays.asList(Row.fromSeq(data))
    val schema = StructType(colNames.zipWithIndex.map { case (col, i) =>
      StructField(col, Literal(data(i)).dataType)
    })

    val spark = SparkSession.builder.getOrCreate()
    val df = spark.createDataFrame(rows, schema)
    model.transform(df).first()
  }

}
