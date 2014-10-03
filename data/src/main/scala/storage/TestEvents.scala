/** Copyright 2014 TappingStone, Inc.
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

package io.prediction.data.storage

//import io.prediction.data.storage.elasticsearch.ESStorageClient
//import io.prediction.data.storage.hbase.HBStorageClient

import io.prediction.data.storage.elasticsearch.ESEvents
import io.prediction.data.storage.hbase.HBEvents

import org.json4s.JObject
import org.json4s.native.JsonMethods.parse
import org.json4s.native.Serialization.{ read, write }

import scala.concurrent.ExecutionContext.Implicits.global

import org.joda.time.DateTime

object TestEvents {

  def main(args: Array[String]) {
    //testESEvents()
    testHBEvents()
  }

  val e = Event(
    event = "$set",
    entityType = "axx",
    entityId = "abc",
    targetEntityType = None,
    targetEntityId = None,
    properties = DataMap(parse("""
      { "numbers" : [1, 2, 3, 4],
        "abc" : "some_string",
        "def" : 4, "k" : false
      } """).asInstanceOf[JObject]),
    eventTime = DateTime.now,
    tags = List("tag1", "tag2"),
    appId = 4,
    predictionKey = None
  )

  val e2 = e.copy(properties=DataMap())

  def testESEvents() {

    /*val config = StorageClientConfig(Seq("localhost"), Seq(9300))
    val storageClient = new ESStorageClient(config)
    val client = storageClient.client
    val eventConnector = storageClient.eventClient*/
    val eventConnector = Storage.getDataObject[Events]("predictionio_events_es").asInstanceOf[ESEvents]
    implicit val formats = eventConnector.formats

    //client.prepareGet("testindex", "events", "Abcdef").get()

    val x = write(e)
    println(x)
    println(x.getClass)

    val de = eventConnector.insert(e)
    println(de)
    de match {
      case Right(d) => {
        val e2 = eventConnector.get(d)
        println(e2)
        val k = eventConnector.delete(d)
        println(k)
        val k2 = eventConnector.delete(d)
        println(k2)
      }
      case _ => {println("match error")}
    }

    val i1 = eventConnector.insert(e)
    println(i1)
    val i2 = eventConnector.insert(e)
    println(i2)
    val i3 = eventConnector.insert(e)
    println(i3)

    // force refresh index for testing, else get may not have result
    //client.admin().indices().prepareRefresh("testindex").get()

    val all = eventConnector.getByAppId(4)
    println(all.right.map{ x =>
      val l = x.toList
      s"size ${l.size}, ${l}"
    })

    val delAll = eventConnector.deleteByAppId(4)
    println(delAll)
    val all2 = eventConnector.getByAppId(4)
    println(all2)
    //client.close()
  }

  def testHBEvents() = {

    println("testHBEvents")

    /*val config = StorageClientConfig(Seq("localhost"), Seq(9300))
    val storageClient = new HBStorageClient(config)
    val client = storageClient.client
    val eventConnector = storageClient.eventClient*/
    val storageClient = new hbase.StorageClient(StorageClientConfig(
      hosts = Seq("localhost"),
      ports = Seq(1234)))

    val eventConnector = new hbase.HBEvents(storageClient.client,
      "predictionio_events_hb")
      //Storage.getDataObject[Events]("predictionio_events_hb").asInstanceOf[HBEvents]

    val de = eventConnector.insert(e)
    println(de)
    de match {
      case Right(d) => {
        val e2 = eventConnector.get(d)
        println(e2)
        val k = eventConnector.delete(d)
        println(k)
        val k2 = eventConnector.delete(d)
        println(k2)
      }
      case _ => {println("match error")}
    }

    val i1 = eventConnector.insert(e)
    println(i1)
    val i2 = eventConnector.insert(e)
    println(i2)
    val i3 = eventConnector.insert(e)
    println(i3)

    val all = eventConnector.getByAppId(4)
    println(all.right.map{ x =>
      val l = x.toList
      s"size ${l.size}, ${l}"
    })

    val delAll = eventConnector.deleteByAppId(4)
    println(delAll)
    val all2 = eventConnector.getByAppId(4)
    println(all2)

    (eventConnector.insert(e2) match {
      case Right(id) => eventConnector.get(id)
      case Left(x) => Left(x)
    }) match {
      case Right(e) => println(e)
      case _ => println("error")
    }

  }
}
