package io.prediction.examples.elasticsearch

import io.prediction.controller._

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import org.apache.hadoop.conf.Configuration
import org.elasticsearch.hadoop.mr.EsInputFormat
import org.apache.hadoop.io.Text
import org.apache.hadoop.io.MapWritable

import io.prediction.data.storage.ItemTrend
import io.prediction.data.storage.ItemTrendSerializer

class PItemTrends(
    @transient val sc: SparkContext, val resource: String, val appid: Int) {

  def get(): RDD[ItemTrend] = {
    val conf = new Configuration()
    conf.set("es.resource", resource)
    conf.set("es.query", s"?q=appid:$appid")

    sc
    .newAPIHadoopRDD(
      conf, 
      classOf[EsInputFormat[Text, MapWritable]],
      classOf[Text], 
      classOf[MapWritable])
    .map(e => ItemTrendSerializer(e._2))
  }
}

case class DataSource
  extends PDataSource[EmptyParams, AnyRef, AnyRef, AnyRef, AnyRef] {

  def read(sc: SparkContext): Seq[(AnyRef, AnyRef, RDD[(AnyRef, AnyRef)])] = {
    val itemTrends = new PItemTrends(
      sc, "predictionio_appdata/itemtrends", 1008)

    itemTrends.get.map(e => (e.id, e.daily.size)).collect
      .take(10).foreach(println)

    Seq[(AnyRef, AnyRef, RDD[(AnyRef, AnyRef)])]()
  }
}

object Run {
  def main(args: Array[String]) {
    Workflow.run(
      dataSourceClassOpt = Some(classOf[DataSource]),
      params = WorkflowParams(
        batch = "ES Test",
        verbose = 3)
    )
  }
}

