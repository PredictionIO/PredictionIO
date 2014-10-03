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

package io.prediction.data.storage.elasticsearch

import io.prediction.data.storage.Event
import io.prediction.data.storage.StorageError
import io.prediction.data.storage.Events
import io.prediction.data.storage.EventJson4sSupport

import grizzled.slf4j.Logging

import org.elasticsearch.ElasticsearchException
import org.elasticsearch.client.Client
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.node.NodeBuilder.nodeBuilder
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.transport.ConnectTransportException
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse
import org.elasticsearch.action.ActionListener
import org.elasticsearch.index.query.FilterBuilders
import org.elasticsearch.index.query.QueryBuilders

import org.json4s.DefaultFormats
import org.json4s.native.Serialization.{ read, write }
//import org.json4s.ext.JodaTimeSerializers

import scala.util.Try
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext

class ESEvents(client: Client, index: String) extends Events with Logging {

  implicit val formats = DefaultFormats + new EventJson4sSupport.DBSerializer
  //implicit val formats = DefaultFormats.lossless ++ JodaTimeSerializers.all

  val typeName = "events"

  override
  def futureInsert(event: Event)(implicit ec: ExecutionContext):
    Future[Either[StorageError, String]] = {
    val response = Promise[IndexResponse]

    client.prepareIndex(index, typeName)
      .setSource(write(event))
      .execute(new ESActionListener(response))

    response.future
      .map(r => Right(r.getId()))
      .recover {
        case e: Exception => Left(StorageError(e.toString))
      }
  }

  override
  def futureGet(eventId: String)(implicit ec: ExecutionContext):
    Future[Either[StorageError, Option[Event]]] = {

    val response = Promise[GetResponse]

    client.prepareGet(index, typeName, eventId)
      .execute(new ESActionListener(response))

    response.future
      .map { r =>
        if (r.isExists)
          Right(Some(read[Event](r.getSourceAsString)))
        else
          Right(None)
      }.recover {
        case e: Exception => Left(StorageError(e.toString))
      }
  }

  override
  def futureDelete(eventId: String)(implicit ec: ExecutionContext):
    Future[Either[StorageError, Boolean]] = {
    val response = Promise[DeleteResponse]

    client.prepareDelete(index, typeName, eventId)
      .execute(new ESActionListener(response))

    response.future
      .map(r => Right(r.isFound()))
      .recover {
        case e: Exception => Left(StorageError(e.toString))
      }
  }

  override
  def futureGetByAppId(appId: Int)(implicit ec: ExecutionContext):
    Future[Either[StorageError, Iterator[Event]]] = {
    val response = Promise[SearchResponse]

    client.prepareSearch(index).setTypes(typeName)
      .setPostFilter(FilterBuilders.termFilter("appId", appId))
      .execute(new ESActionListener(response))

    response.future
      .map{ r =>
        val dataIt = r.getHits().hits()
          .map(h => read[Event](h.getSourceAsString)).toIterator
        Right(dataIt)
      }.recover {
        case e: Exception => Left(StorageError(e.toString))
      }

  }

  override
  def futureDeleteByAppId(appId: Int)(implicit ec: ExecutionContext):
    Future[Either[StorageError, Unit]] = {

    val response = Promise[DeleteByQueryResponse]

    client.prepareDeleteByQuery(index).setTypes(typeName)
      .setQuery(QueryBuilders.termQuery("appId", appId))
      .execute(new ESActionListener(response))

    response.future
      .map { r =>
        val indexResponse = r.getIndex(index)
        val numFailures = indexResponse.getFailedShards()
        if (numFailures != 0)
          Left(StorageError(s"Failed to delete ${numFailures} shards."))
        else
          Right(())
      }.recover {
        case e: Exception => Left(StorageError(e.toString))
      }

  }

}


class ESActionListener[T](val p: Promise[T]) extends ActionListener[T]{
  override def onResponse(r: T) = {
    p.success(r)
  }
  override def onFailure(e: Throwable) = {
    p.failure(e)
  }
}
