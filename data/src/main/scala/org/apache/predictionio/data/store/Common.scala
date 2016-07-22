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

package org.apache.predictionio.data.store

import org.apache.predictionio.data.storage.Storage
import grizzled.slf4j.Logger

private[predictionio] object Common {

  @transient lazy val logger = Logger[this.type]
  @transient lazy private val appsDb = Storage.getMetaDataApps()
  @transient lazy private val channelsDb = Storage.getMetaDataChannels()

  /* throw exception if invalid app name or channel name */
  def appNameToId(appName: String, channelName: Option[String]): (Int, Option[Int]) = {
    val appOpt = appsDb.getByName(appName)

    appOpt.map { app =>
      val channelMap: Map[String, Int] = channelsDb.getByAppid(app.id)
        .map(c => (c.name, c.id)).toMap

      val channelId: Option[Int] = channelName.map { ch =>
        if (channelMap.contains(ch)) {
          channelMap(ch)
        } else {
          logger.error(s"Invalid channel name ${ch}.")
          throw new IllegalArgumentException(s"Invalid channel name ${ch}.")
        }
      }

      (app.id, channelId)
    }.getOrElse {
      logger.error(s"Invalid app name ${appName}")
      throw new IllegalArgumentException(s"Invalid app name ${appName}")
    }
  }
}
