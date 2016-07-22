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

package org.apache.predictionio.tools.console

import org.apache.predictionio.data.storage

import grizzled.slf4j.Logging

case class AccessKeyArgs(
  accessKey: String = "",
  events: Seq[String] = Seq())

object AccessKey extends Logging {
  def create(ca: ConsoleArgs): Int = {
    val apps = storage.Storage.getMetaDataApps
    apps.getByName(ca.app.name) map { app =>
      val accessKeys = storage.Storage.getMetaDataAccessKeys
      val accessKey = accessKeys.insert(storage.AccessKey(
        key = ca.accessKey.accessKey,
        appid = app.id,
        events = ca.accessKey.events))
      accessKey map { k =>
        info(s"Created new access key: ${k}")
        0
      } getOrElse {
        error(s"Unable to create new access key.")
        1
      }
    } getOrElse {
      error(s"App ${ca.app.name} does not exist. Aborting.")
      1
    }
  }

  def list(ca: ConsoleArgs): Int = {
    val keys =
      if (ca.app.name == "") {
        storage.Storage.getMetaDataAccessKeys.getAll
      } else {
        val apps = storage.Storage.getMetaDataApps
        apps.getByName(ca.app.name) map { app =>
          storage.Storage.getMetaDataAccessKeys.getByAppid(app.id)
        } getOrElse {
          error(s"App ${ca.app.name} does not exist. Aborting.")
          return 1
        }
      }
    val title = "Access Key(s)"
    info(f"$title%64s | App ID | Allowed Event(s)")
    keys.sortBy(k => k.appid) foreach { k =>
      val events =
        if (k.events.size > 0) k.events.sorted.mkString(",") else "(all)"
      info(f"${k.key}%64s | ${k.appid}%6d | $events%s")
    }
    info(s"Finished listing ${keys.size} access key(s).")
    0
  }

  def delete(ca: ConsoleArgs): Int = {
    try {
      storage.Storage.getMetaDataAccessKeys.delete(ca.accessKey.accessKey)
      info(s"Deleted access key ${ca.accessKey.accessKey}.")
      0
    } catch {
      case e: Exception =>
        error(s"Error deleting access key ${ca.accessKey.accessKey}.", e)
        1
    }
  }
}
