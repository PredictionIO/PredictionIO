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

package org.apache.predictionio.tools.commands

import org.apache.predictionio.data.storage
import org.apache.predictionio.tools.EitherLogging
import org.apache.predictionio.tools.ReturnTypes._

object AccessKey extends EitherLogging {

  def create(
    appName: String,
    key: String,
    events: Seq[String]): Expected[storage.AccessKey] = {

    val apps = storage.Storage.getMetaDataApps
    apps.getByName(appName) map { app =>
      val accessKeys = storage.Storage.getMetaDataAccessKeys
      val newKey = storage.AccessKey(
        key = key,
        appid = app.id,
        events = events)
      accessKeys.insert(newKey) map { k =>
        info(s"Created new access key: ${k}")
        Right(newKey.copy(key = k))
      } getOrElse {
        logAndFail(s"Unable to create new access key.")
      }
    } getOrElse {
      logAndFail(s"App ${appName} does not exist. Aborting.")
    }
  }

  def list(app: Option[String]): Expected[Seq[storage.AccessKey]] =
    app map { appName =>
      App.show(appName).right map { appChansPair => appChansPair._1.keys }
    } getOrElse {
      Right(storage.Storage.getMetaDataAccessKeys.getAll)
    }

  def delete(key: String): MaybeError = {
    try {
      storage.Storage.getMetaDataAccessKeys.delete(key)
      logAndSucceed(s"Deleted access key ${key}.")
    } catch {
      case e: Exception =>
        error(s"Error deleting access key ${key}.", e)
        Left(s"Error deleting access key ${key}.")
    }
  }
}
