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

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.io.{Text, MapWritable}
import org.apache.predictionio.data.storage.{StorageClientConfig, PEvents, Event}
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.hadoop.mr.EsInputFormat
import org.elasticsearch.spark.rdd.EsSpark
import org.joda.time.DateTime

// TODO for elasticsearch
import org.apache.spark.SparkConf
import org.elasticsearch.spark._


class ESPEvents( client: TransportClient, config: StorageClientConfig, namespace: String)
  extends PEvents {

  override
  def find(
    appId: Int,
    channelId: Option[Int] = None,
    startTime: Option[DateTime] = None,
    untilTime: Option[DateTime] = None,
    entityType: Option[String] = None,
    entityId: Option[String] = None,
    eventNames: Option[Seq[String]] = None,
    targetEntityType: Option[Option[String]] = None,
    targetEntityId: Option[Option[String]] = None
    )(sc: SparkContext): RDD[Event] = {

    // TODO: ES Hadoop Configuration Builder 的なものがあるかを調査
    // https://www.elastic.co/guide/en/elasticsearch/hadoop/current/configuration.html

    // TODO: jacksonを使って書く & ESEventsUtilへ移動
    val must_query = Seq(
      entityType.map(x => s"""{"term":{"entityType":"${x}"}}"""),
      targetEntityType.flatMap(xx => xx.map(x => s"""{"term":{"targetEntityType":"${x}"}}""")),
      eventNames
        .map{xx => xx.map(x => "\"%s\"".format(x))}
        .map(x => s"""{"terms":{"event":[${x.mkString(",")}]}}""")
    ).flatten.mkString(",")
    val query = s"""{"query":{"bool":{"must":[${must_query}]}}}"""

    val conf = new Configuration()
    conf.set("es.resource", "pio_event/events"); // TODO: Index/Type などPIOのルールを調べる
    conf.set("es.query", query); // TODO: クエリは動的に生成する

    val rdd = sc.newAPIHadoopRDD(conf, classOf[EsInputFormat[Text, MapWritable]],
      classOf[Text], classOf[MapWritable]
    ).map {
      case (key, doc) => {
        ESEventsUtil.resultToEvent(key, doc, appId)
      }
    }

    rdd
  }

  override
  def write(events: RDD[Event], appId: Int, channelId: Option[Int])(sc: SparkContext): Unit = {
    val conf = new Configuration()
    conf.set("es.resource.write", "pio_event/events"); // TODO Index/Type などPIOのルールを調べる
    conf.set("es.query", "?q=me*"); // TODO クエリを決める

    events.map { event =>
      ESEventsUtil.eventToPut(event, appId)
    }.saveToEs("pio/events")
  }

  override
  def delete(eventIds: RDD[String], appId: Int, channelId: Option[Int])(sc: SparkContext): Unit = {
    ???
  }

}
