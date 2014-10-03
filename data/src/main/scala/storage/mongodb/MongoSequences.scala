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

package io.prediction.data.storage.mongodb

import com.github.nscala_time.time.Imports._
import com.mongodb.casbah.Imports.MongoDB
import com.mongodb.casbah.query.Imports._

/** Provides incremental sequence number generation. */
class MongoSequences(db: MongoDB) {
  private val seqColl = db("seq")

  /** Get the next sequence number from the given sequence name. */
  def genNext(name: String): Int = {
    val qFind = MongoDBObject("_id" -> name)
    val qField = MongoDBObject("next" -> 1)
    val qSort = MongoDBObject()
    val qRemove = false
    val qModify = $inc("next" -> 1)
    val qReturnNew = true
    val qUpsert = true
    seqColl.findAndModify(qFind, qField, qSort, qRemove, qModify,
      qReturnNew, qUpsert).get.getAsOrElse[Number]("next", 0).intValue
  }

  def genNextDaily(name: String): Int = {
    val today = DateTime.now.hour(0).minute(0).second(0).millis / 1000
    val qFind = MongoDBObject("_id" -> s"$name$today")
    val qField = MongoDBObject("next" -> 1)
    val qSort = MongoDBObject()
    val qRemove = false
    val qModify = $inc("next" -> 1)
    val qReturnNew = true
    val qUpsert = true
    seqColl.findAndModify(qFind, qField, qSort, qRemove, qModify,
      qReturnNew, qUpsert).get.getAsOrElse[Number]("next", 0).intValue
  }
}
