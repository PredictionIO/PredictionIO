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

import org.apache.predictionio.controller._
import org.apache.predictionio.workflow.KryoInstantiator
import org.apache.spark.SparkContext
import org.apache.spark.ml.PipelineModel
import org.apache.spark.sql.catalyst.expressions.Literal
import org.apache.spark.sql.types.{StructField, StructType}
import org.apache.spark.sql.{Row, SparkSession}


object PythonEngine extends EngineFactory {

  private[engine] type Query = Map[String, Any]

  def apply(): Engine[EmptyTrainingData, EmptyEvaluationInfo, EmptyPreparedData,
    Query, Row, EmptyActualResult] = {
    new Engine(
      classOf[PythonDataSource],
      classOf[PythonPreparator],
      Map("default" -> classOf[PythonAlgorithm]),
      classOf[PythonServing])
  }

  def models(model: PipelineModel): Array[Byte] = {
    val kryo = KryoInstantiator.newKryoInjection
    kryo(Seq(model))
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

object PythonServing {
  private[engine] val columns = "PythonPredictColumns"

  case class Params(columns: Seq[String]) extends org.apache.predictionio.controller.Params
}

class PythonServing(params: PythonServing.Params) extends LFirstServing[Query, Row] {
  override def supplement(q: Query): Query = {
    q + (PythonServing.columns -> params.columns)
  }
}

class PythonAlgorithm extends
  P2LAlgorithm[EmptyPreparedData, PipelineModel, Query, Row] {

  def train(sc: SparkContext, data: EmptyPreparedData): PipelineModel = ???

  def predict(model: PipelineModel, query: Query): Row = {
    val selectCols = query(PythonServing.columns).asInstanceOf[Seq[String]]
    val (colNames, data) = (query - PythonServing.columns).toList.unzip

    val rows = Arrays.asList(Row.fromSeq(data))
    val schema = StructType(colNames.zipWithIndex.map { case (col, i) =>
      StructField(col, Literal(data(i)).dataType)
    })

    val spark = SparkSession.builder.getOrCreate()
    val df = spark.createDataFrame(rows, schema)
    model.transform(df)
      .select(selectCols.head, selectCols.tail: _*)
      .first()
  }

}
