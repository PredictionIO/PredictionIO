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

package org.apache.predictionio.data

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

import java.lang.IllegalArgumentException

private[predictionio] object Utils {

  // use dateTime() for strict ISO8601 format
  val dateTimeFormatter = ISODateTimeFormat.dateTime().withOffsetParsed()

  val dateTimeNoMillisFormatter =
    ISODateTimeFormat.dateTimeNoMillis().withOffsetParsed()

  def stringToDateTime(dt: String): DateTime = {
    // We accept two formats.
    // 1. "yyyy-MM-dd'T'HH:mm:ss.SSSZZ"
    // 2. "yyyy-MM-dd'T'HH:mm:ssZZ"
    // The first one also takes milliseconds into account.
    try {
      // formatting for "yyyy-MM-dd'T'HH:mm:ss.SSSZZ"
      dateTimeFormatter.parseDateTime(dt)
    } catch {
      case e: IllegalArgumentException => {
        // handle when the datetime string doesn't specify milliseconds.
        dateTimeNoMillisFormatter.parseDateTime(dt)
      }
    }
  }

  def dateTimeToString(dt: DateTime): String = dateTimeFormatter.print(dt)
    // dt.toString

}
