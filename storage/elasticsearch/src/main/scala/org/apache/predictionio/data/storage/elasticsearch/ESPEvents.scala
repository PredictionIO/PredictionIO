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

package org.apache.predictionio.data.storage.elasticsearch

import scala.collection.JavaConverters._

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.io.MapWritable
import org.apache.hadoop.io.Text
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.storage.PEvents
import org.apache.predictionio.data.storage.StorageClientConfig
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.elasticsearch.client.RestClient
import org.elasticsearch.hadoop.mr.EsInputFormat
import org.elasticsearch.spark._
import org.joda.time.DateTime
import java.io.IOException
import org.apache.http.util.EntityUtils
import org.apache.http.nio.entity.NStringEntity
import org.apache.http.entity.ContentType
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.ext.JodaTimeSerializers


class ESPEvents(client: RestClient, config: StorageClientConfig, baseIndex: String)
    extends PEvents {
  implicit val formats = DefaultFormats.lossless ++ JodaTimeSerializers.all

  def getEsType(appId: Int, channelId: Option[Int] = None): String = {
    channelId.map { ch =>
      s"${appId}_${ch}"
    }.getOrElse {
      s"${appId}"
    }
  }

  def getESNodes(): String = {
    val hosts = config.properties.get("HOSTS").
      map(_.split(",").toSeq).getOrElse(Seq("localhost"))
    val ports = config.properties.get("PORTS").
      map(_.split(",").toSeq.map(_.toInt)).getOrElse(Seq(9200))
    (hosts, ports).zipped.map(
      (h, p) => s"$h:$p").mkString(",")
  }

  override def find(
    appId: Int,
    channelId: Option[Int] = None,
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None,
    entityType: Option[String] = None,
    entityId: Option[String] = None,
    eventNames: Option[Seq[String]] = None,
    targetEntityType: Option[Option[String]] = None,
    targetEntityId: Option[Option[String]] = None)(sc: SparkContext): RDD[Event] = {

    val query = ESUtils.createEventQuery(
      startTime, untilTime, entityType, entityId,
      eventNames, targetEntityType, targetEntityId, None)

    val estype = getEsType(appId, channelId)
    val index = baseIndex + "_" + estype
    val conf = new Configuration()
    conf.set("es.resource", s"$index/$estype")
    conf.set("es.query", query)
    conf.set("es.nodes", getESNodes())

    val rdd = sc.newAPIHadoopRDD(conf, classOf[EsInputFormat[Text, MapWritable]],
      classOf[Text], classOf[MapWritable]).map {
        case (key, doc) => {
          ESEventsUtil.resultToEvent(key, doc, appId)
        }
      }

    rdd
  }

  override def write(
    events: RDD[Event],
    appId: Int, channelId: Option[Int])(sc: SparkContext): Unit = {
    val estype = getEsType(appId, channelId)
    val index = baseIndex + "_" + estype
    val conf = Map("es.resource" -> s"$index/$estype", "es.nodes" -> getESNodes())
    events.map { event =>
      ESEventsUtil.eventToPut(event, appId)
    }.saveToEs(conf)
  }

  override def delete(
    eventIds: RDD[String],
    appId: Int, channelId: Option[Int])(sc: SparkContext): Unit = {
    val estype = getEsType(appId, channelId)
    val index = baseIndex + "_" + estype
      eventIds.foreachPartition { iter =>
        iter.foreach { eventId =>
          try {
            val json =
              ("query" ->
                ("term" ->
                  ("eventId" -> eventId)))
            val entity = new NStringEntity(compact(render(json)), ContentType.APPLICATION_JSON)
            val response = client.performRequest(
              "POST",
              s"/$index/$estype/_delete_by_query",
              Map("refresh" -> ESUtils.getEventDataRefresh(config)).asJava,
              entity)
          val jsonResponse = parse(EntityUtils.toString(response.getEntity))
          val result = (jsonResponse \ "result").extract[String]
          result match {
            case "deleted" =>
            case _ =>
              logger.error(s"[$result] Failed to update $index/$estype:$eventId")
          }
        } catch {
          case e: IOException =>
            logger.error(s"Failed to update $index/$estype:$eventId", e)
        }
      }
    }
  }

}
