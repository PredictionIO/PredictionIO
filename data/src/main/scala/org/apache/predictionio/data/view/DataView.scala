/** Copyright 2015 TappingStone, Inc.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package org.apache.predictionio.data.view

import org.apache.predictionio.annotation.Experimental
import org.apache.predictionio.data.storage.Event

import grizzled.slf4j.Logger
import org.apache.predictionio.data.store.PEventStore

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.joda.time.DateTime

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.util.hashing.MurmurHash3

/**
 * :: Experimental ::
 */
@Experimental
object DataView {
  /**
    * :: Experimental ::
    *
    * Create a DataFrame from events of a specified app.
    *
    * @param appName return events of this app
    * @param channelName use events of this channel (default channel if it's None)
    * @param startTime return events with eventTime >= startTime
    * @param untilTime return events with eventTime < untilTime
    * @param conversionFunction a function that turns raw Events into events of interest.
    *                           If conversionFunction returns None, such events are dropped.
    * @param name identify the DataFrame created
    * @param version used to track changes to the conversionFunction, e.g. version = "20150413"
    *                and update whenever the function is changed.
    * @param sqlContext SQL context
    * @tparam E the output type of the conversion function. The type needs to extend Product
    *           (e.g. case class)
    * @return a DataFrame of events
    */
  @Experimental
  def create[E <: Product: TypeTag: ClassTag](
    appName: String,
    channelName: Option[String] = None,
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None,
    conversionFunction: Event => Option[E],
    name: String = "",
    version: String = "")(sqlContext: SQLContext): DataFrame = {

    @transient lazy val logger = Logger[this.type]

    val sc = sqlContext.sparkContext

    val beginTime = startTime match {
      case Some(t) => t
      case None => new DateTime(0L)
    }
    val endTime = untilTime match {
      case Some(t) => t
      case None => DateTime.now() // fix the current time
    }
    // detect changes to the case class
    val uid = java.io.ObjectStreamClass.lookup(implicitly[reflect.ClassTag[E]].runtimeClass)
        .getSerialVersionUID
    val hash = MurmurHash3.stringHash(s"$beginTime-$endTime-$version-$uid")
    val baseDir = s"${sys.env("PIO_FS_BASEDIR")}/view"
    val fileName = s"$baseDir/$name-$appName-$hash.parquet"
    try {
      sqlContext.parquetFile(fileName)
    } catch {
      case e: java.io.FileNotFoundException =>
        logger.info("Cached copy not found, reading from DB.")
        // if cached copy is found, use it. If not, grab from Storage
        val result: RDD[E] = PEventStore.find(
            appName = appName,
            channelName = channelName,
            startTime = startTime,
            untilTime = Some(endTime))(sc)
          .flatMap((e) => conversionFunction(e))
        import sqlContext.implicits._ // needed for RDD.toDF()
        val resultDF = result.toDF()

        resultDF.saveAsParquetFile(fileName)
        sqlContext.parquetFile(fileName)
      case e: java.lang.RuntimeException =>
        if (e.toString.contains("is not a Parquet file")) {
          logger.error(s"$fileName does not contain a valid Parquet file. " +
            "Please delete it and try again.")
        }
        throw e
    }
  }
}
