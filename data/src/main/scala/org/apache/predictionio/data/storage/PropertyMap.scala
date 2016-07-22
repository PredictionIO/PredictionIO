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
package org.apache.predictionio.data.storage

import org.joda.time.DateTime

import org.json4s.JValue
import org.json4s.JObject
import org.json4s.native.JsonMethods.parse

/** A PropertyMap stores aggregated properties of the entity.
  * Internally it is a Map
  * whose keys are property names and values are corresponding JSON values
  * respectively. Use the get() method to retrieve the value of mandatory
  * property or use getOpt() to retrieve the value of the optional property.
  *
  * @param fields Map of property name to JValue
  * @param firstUpdated first updated time of this PropertyMap
  * @param lastUpdated last updated time of this PropertyMap
  */
class PropertyMap(
  fields: Map[String, JValue],
  val firstUpdated: DateTime,
  val lastUpdated: DateTime
) extends DataMap(fields) {

  override
  def toString: String = s"PropertyMap(${fields}, ${firstUpdated}, ${lastUpdated})"

  override
  def hashCode: Int =
    41 * (
      41 * (
        41 + fields.hashCode
      ) + firstUpdated.hashCode
    ) + lastUpdated.hashCode

  override
  def equals(other: Any): Boolean = other match {
    case that: PropertyMap => {
      (that.canEqual(this)) &&
      (super.equals(that)) &&
      (this.firstUpdated.equals(that.firstUpdated)) &&
      (this.lastUpdated.equals(that.lastUpdated))
    }
    case that: DataMap => { // for testing purpose
      super.equals(that)
    }
    case _ => false
  }

  override
  def canEqual(other: Any): Boolean = other.isInstanceOf[PropertyMap]
}

/** Companion object of the [[PropertyMap]] class. */
object PropertyMap {

  /** Create an PropertyMap from a Map of String to JValue,
    * firstUpdated and lastUpdated time.
    *
    * @param fields a Map of String to JValue
    * @param firstUpdated First updated time
    * @param lastUpdated Last updated time
    * @return a new PropertyMap
    */
  def apply(fields: Map[String, JValue],
    firstUpdated: DateTime, lastUpdated: DateTime): PropertyMap =
    new PropertyMap(fields, firstUpdated, lastUpdated)

  /** Create an PropertyMap from a JSON String and firstUpdated and lastUpdated
    * time.
    * @param js JSON String. eg """{ "a": 1, "b": "foo" }"""
    * @param firstUpdated First updated time
    * @param lastUpdated Last updated time
    * @return a new PropertyMap
    */
  def apply(js: String, firstUpdated: DateTime, lastUpdated: DateTime)
  : PropertyMap = apply(
      fields = parse(js).asInstanceOf[JObject].obj.toMap,
      firstUpdated = firstUpdated,
      lastUpdated = lastUpdated
    )
}
