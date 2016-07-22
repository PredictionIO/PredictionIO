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

import org.apache.predictionio.annotation.DeveloperApi

/** :: DeveloperApi ::
  * Stores mapping of channel IDs, names and app ID
  *
  * @param id ID of the channel
  * @param name Name of the channel (must be unique within the same app)
  * @param appid ID of the app which this channel belongs to
  * @group Meta Data
  */
@DeveloperApi
case class Channel(
  id: Int,
  name: String, // must be unique within the same app
  appid: Int
) {
  require(Channel.isValidName(name),
    "Invalid channel name: ${name}. ${Channel.nameConstraint}")
}

/** :: DeveloperApi ::
  * Companion object of [[Channel]]
  *
  * @group Meta Data
  */
@DeveloperApi
object Channel {
  /** Examine whether the supplied channel name is valid. A valid channel name
    * must consists of 1 to 16 alphanumeric and '-' characters.
    *
    * @param s Channel name to examine
    * @return true if channel name is valid, false otherwise
    */
  def isValidName(s: String): Boolean = {
    // note: update channelNameConstraint if this rule is changed
    s.matches("^[a-zA-Z0-9-]{1,16}$")
  }

  /** For consistent error message display */
  val nameConstraint: String =
    "Only alphanumeric and - characters are allowed and max length is 16."
}

/** :: DeveloperApi ::
  * Base trait of the [[Channel]] data access object
  *
  * @group Meta Data
  */
@DeveloperApi
trait Channels {
  /** Insert a new [[Channel]]. Returns a generated channel ID if original ID is 0. */
  def insert(channel: Channel): Option[Int]

  /** Get a [[Channel]] by channel ID */
  def get(id: Int): Option[Channel]

  /** Get all [[Channel]] by app ID */
  def getByAppid(appid: Int): Seq[Channel]

  /** Delete a [[Channel]] */
  def delete(id: Int): Unit
}
