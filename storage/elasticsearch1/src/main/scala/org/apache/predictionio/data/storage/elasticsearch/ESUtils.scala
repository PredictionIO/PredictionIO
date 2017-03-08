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

import org.elasticsearch.action.search.SearchRequestBuilder
import org.elasticsearch.client.Client
import org.elasticsearch.common.unit.TimeValue
import org.json4s.Formats
import org.json4s.native.Serialization.read

import scala.collection.mutable.ArrayBuffer

object ESUtils {
  val scrollLife = new TimeValue(60000)

  def getAll[T : Manifest](
      client: Client,
      builder: SearchRequestBuilder)(
      implicit formats: Formats): Seq[T] = {
    val results = ArrayBuffer[T]()
    var response = builder.setScroll(scrollLife).get
    var hits = response.getHits().hits()
    results ++= hits.map(h => read[T](h.getSourceAsString))
    while (hits.size > 0) {
      response = client.prepareSearchScroll(response.getScrollId).
        setScroll(scrollLife).get
      hits = response.getHits().hits()
      results ++= hits.map(h => read[T](h.getSourceAsString))
    }
    results
  }
}
