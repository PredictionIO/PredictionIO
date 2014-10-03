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

import org.json4s._

import scala.collection.GenTraversableOnce

case class DataMapException(msg: String, cause: Exception)
  extends Exception(msg, cause) {

  def this(msg: String) = this(msg, null)
}

case class DataMap (
  val fields: Map[String, JValue]
) extends Serializable {
  lazy implicit val formats = DefaultFormats +
    new DateTimeJson4sSupport.serializer

  def require(name: String) = {
    if (!fields.contains(name))
      throw new DataMapException(s"The field ${name} is required.")
  }

  def get[T: Manifest](name: String): T = {
    require(name)
    fields(name) match {
      case JNull => throw new DataMapException(
        s"The required field ${name} cannot be null.")
      case x: JValue => x.extract[T]
    }
  }
  // TODO: combine getOpt and get
  def getOpt[T: Manifest](name: String): Option[T] = {
    // either the field doesn't exist or its value is null
    fields.get(name).flatMap(_.extract[Option[T]])
  }

  def getOrElse[T: Manifest](name: String, default: T) = {
    getOpt[T](name).getOrElse(default)
  }

  def ++ (that: DataMap) = DataMap(this.fields ++ that.fields)

  def -- (that: GenTraversableOnce[String]) =
    DataMap(this.fields -- that)

  def isEmpty = fields.isEmpty

  def keySet = this.fields.keySet

  def toList(): List[(String, JValue)] = fields.toList

  def toJObject(): JObject = JObject(toList())

}

object DataMap {
  def apply(): DataMap = DataMap(Map[String, JValue]())

  def apply(jObj: JObject): DataMap = {
    if (jObj == null) DataMap() else DataMap(jObj.obj.toMap)
  }
}
