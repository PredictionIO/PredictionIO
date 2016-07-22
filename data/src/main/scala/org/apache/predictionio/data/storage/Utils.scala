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
import org.joda.time.format.ISODateTimeFormat

/** Backend-agnostic storage utilities. */
private[predictionio] object Utils {
  /**
   * Add prefix to custom attribute keys.
   */
  def addPrefixToAttributeKeys[T](
      attributes: Map[String, T],
      prefix: String = "ca_"): Map[String, T] = {
    attributes map { case (k, v) => (prefix + k, v) }
  }

  /** Remove prefix from custom attribute keys. */
  def removePrefixFromAttributeKeys[T](
      attributes: Map[String, T],
      prefix: String = "ca_"): Map[String, T] = {
    attributes map { case (k, v) => (k.stripPrefix(prefix), v) }
  }

  /**
   * Appends App ID to any ID.
   * Used for distinguishing different app's data within a single collection.
   */
  def idWithAppid(appid: Int, id: String): String = appid + "_" + id

  def stringToDateTime(dt: String): DateTime =
    ISODateTimeFormat.dateTimeParser.parseDateTime(dt)
}
