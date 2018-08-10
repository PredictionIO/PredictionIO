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
import org.apache.predictionio.data.storage.Channel
import org.apache.predictionio.tools.EitherLogging
import org.apache.predictionio.tools.ReturnTypes._

sealed case class AppDescription(
  app: storage.App,
  keys: Seq[storage.AccessKey])

object App extends EitherLogging {

  def create(
    name: String,
    id: Option[Int] = None,
    description: Option[String] = None,
    accessKey: String = "") : Expected[AppDescription] = {

    val apps = storage.Storage.getMetaDataApps()
    // get the client in the beginning so error exit right away if can't access client
    val events = storage.Storage.getLEvents()
    var errStr = ""

    apps.getByName(name) map { app =>
      errStr = s"App ${name} already exists. Aborting."
      error(errStr)
      errStr
    } orElse {
      id.flatMap { id =>
        apps.get(id) map { app =>
          errStr = s"App ID ${id} already exists and maps to the app '${app.name}'. " +
            "Aborting."
          error(errStr)
          errStr
        }
      }
    } map {err => Left(err)} getOrElse {
      val newApp = storage.App(
        id = id.getOrElse(0),
        name = name,
        description = description)
      val appid = apps.insert(newApp)

      appid map { id =>
        val dbInit = events.init(id)
        val r = if (dbInit) {
          info(s"Initialized Event Store for this app ID: ${id}.")
          val accessKeys = storage.Storage.getMetaDataAccessKeys
          val newKey = storage.AccessKey(
            key = accessKey,
            appid = id,
            events = Nil)
          accessKeys.insert(newKey)
          .map { k =>
            Right(AppDescription(
              app = newApp.copy(id = id),
              keys = Seq(newKey.copy(key = k))))
          } getOrElse {
            logAndFail(s"Unable to create new access key.")
          }
        } else {
          errStr = s"Unable to initialize Event Store for this app ID: ${id}."
          try {
            apps.delete(id)
          } catch {
            case e: Exception =>
              errStr += s"""
                |Failed to revert back the App meta-data change.
                |The app ${name} CANNOT be used!
                |Please run 'pio app delete ${name}' to delete this app!""".stripMargin
          }
          logAndFail(errStr)
        }
        events.close()
        r
      } getOrElse {
        logAndFail(s"Unable to create new app.")
      }
    }
  }

  def list: Seq[AppDescription] = {
    val apps = storage.Storage.getMetaDataApps.getAll().sortBy(_.name)
    val accessKeys = storage.Storage.getMetaDataAccessKeys

    apps map { app =>
      AppDescription(
        app = app,
        keys = accessKeys.getByAppid(app.id))
    }
  }

  def show(appName: String): Expected[(AppDescription, Seq[Channel])] = {
    val apps = storage.Storage.getMetaDataApps
    val accessKeys = storage.Storage.getMetaDataAccessKeys
    val channels = storage.Storage.getMetaDataChannels

    apps.getByName(appName) map { app =>
      Right(
        (AppDescription(
          app = app,
          keys = accessKeys.getByAppid(app.id)),
        channels.getByAppid(app.id))
      )
    } getOrElse {
      logAndFail(s"App ${appName} does not exist. Aborting.")
    }
  }

  def delete(name: String): MaybeError = {
    val events = storage.Storage.getLEvents()
    try {
      show(name).right.flatMap { case (appDesc: AppDescription, channels: Seq[Channel]) =>

        val delChannelStatus: MaybeError =
          channels.map { ch =>
            if (events.remove(appDesc.app.id, Some(ch.id))) {
              info(s"Removed Event Store of the channel ID: ${ch.id}")
              try {
                storage.Storage.getMetaDataChannels.delete(ch.id)
                info(s"Deleted channel ${ch.name}")
                None
              } catch {
                case e: Exception =>
                  val errStr = s"Error deleting channel ${ch.name}."
                  error(errStr, e)
                  Some(errStr)
              }
              } else {
                val errStr = s"Error removing Event Store of the channel ID: ${ch.id}."
                error(errStr)
                Some(errStr)
              }
          }
          .flatten
          .reduceOption(_ + "\n" + _)
          .map(Left(_)) getOrElse Success

          if (delChannelStatus.isLeft) {
            return delChannelStatus
          }

          try {
            events.remove(appDesc.app.id)
            info(s"Removed Event Store for this app ID: ${appDesc.app.id}")
          } catch {
            case e: Exception =>
              logAndFail(s"Error removing Event Store for this app. Aborting.")
          }

          appDesc.keys foreach { key =>
            try {
              storage.Storage.getMetaDataAccessKeys.delete(key.key)
              info(s"Removed access key ${key.key}")
            } catch {
              case e: Exception =>
                logAndFail(s"Error removing access key ${key.key}. Aborting.")
            }
          }

          try {
            storage.Storage.getMetaDataApps.delete(appDesc.app.id)
            info(s"Deleted app ${appDesc.app.name}.")
          } catch {
            case e: Exception =>
              logAndFail(s"Error deleting app ${appDesc.app.name}. Aborting.")
          }
          logAndSucceed("Done.")
      }

    } finally {
      events.close()
    }
  }

  def dataDelete(
    name: String,
    channel: Option[String] = None,
    all: Boolean = false): MaybeError = {

    var errStr = ""
    val events = storage.Storage.getLEvents()
    try {
      show(name).right.flatMap { case (appDesc: AppDescription, channels: Seq[Channel]) =>

        val chanIdsToRemove: Seq[Option[Int]] =
          if (all) {
            channels.map(ch => Some(ch.id)) :+ None // remove default channel too
          } else {
            channel.map { chName =>
              channels.find(ch => ch.name == chName) match {
                case None =>
                  return logAndFail(s"""Unable to delete data for channel.
                              |Channel ${chName} doesn't exist.""".stripMargin)
                case Some(ch) => Seq(Some(ch.id))
              }
            } getOrElse {
              Seq(None) // for default channel
            }
          }

        chanIdsToRemove.map { chId: Option[Int] =>

          val r1 = if (events.remove(appDesc.app.id, chId)) {
            if (chId.isDefined) {
              info(s"Removed Event Store for the channel ID: ${chId.get}")
            } else {
              info(s"Removed Event Store for the app ID: ${appDesc.app.id}")
            }
            None
          } else {
            errStr =
              if (chId.isDefined) s"Error removing Event Store for the channel ID: ${chId.get}."
              else s"Error removing Event Store for the app ID: ${appDesc.app.id}."
            error(errStr)
            Some(errStr)
          }
          // re-create table
          val dbInit = events.init(appDesc.app.id, chId)
          val r2 = if (dbInit) {
            if (chId.isDefined) {
              info(s"Initialized Event Store for the channel ID: ${chId.get}")
            } else {
              info(s"Initialized Event Store for the app ID: ${appDesc.app.id}")
            }
            None
          } else {
            errStr =
              if (chId.isDefined) {
                s"Unable to initialize Event Store for the channel ID: ${chId.get}."
              } else {
                s"Unable to initialize Event tore for the app ID: ${appDesc.app.id}."
              }
            error(errStr)
            Some(errStr)
          }
          Seq(r1, r2)
        }
        .flatten.flatten
        .reduceOption(_ + "\n" + _)
        .toLeft(Ok())
      }
    } finally {
      events.close()
    }
  }

  def channelNew(appName: String, newChannel: String): Expected[Channel] = {
    val events = storage.Storage.getLEvents()
    val chanStorage = storage.Storage.getMetaDataChannels
    var errStr = ""
    try {
      show(appName).right flatMap { case (appDesc: AppDescription, channels: Seq[Channel]) =>
        if (channels.find(ch => ch.name == newChannel).isDefined) {
          logAndFail(s"""Channel ${newChannel} already exists.
                      |Unable to create new channel.""".stripMargin)
        } else if (!storage.Channel.isValidName(newChannel)) {
          logAndFail(s"""Unable to create new channel.
                      |The channel name ${newChannel} is invalid.
                      |${storage.Channel.nameConstraint}""".stripMargin)
        } else {

          val channel = Channel(
            id = 0,
            appid = appDesc.app.id,
            name = newChannel)

          chanStorage.insert(channel) map { chanId =>

            info(s"Updated Channel meta-data.")

            // initialize storage
            val dbInit = events.init(appDesc.app.id, Some(chanId))
            if (dbInit) {
              info(s"Initialized Event Store for the channel: ${newChannel}.")
              info(s"Created new channel:")
              info(s"    Channel Name: ${newChannel}")
              info(s"      Channel ID: ${chanId}")
              info(s"          App ID: ${appDesc.app.id}")
              Right(channel.copy(id = chanId))
            } else {
              errStr = s"""Unable to create new channel.
                          |Failed to initialize Event Store.""".stripMargin
              error(errStr)
              // reverted back the meta data
              try {
                chanStorage.delete(chanId)
                Left(errStr)
              } catch {
                case e: Exception =>
                  val nextErrStr = (s"""
                    |Failed to revert back the Channel meta-data change.
                    |The channel ${newChannel} CANNOT be used!
                    |Please run 'pio app channel-delete ${appName} ${newChannel}'""" +
                    " to delete this channel!").stripMargin
                  logAndFail(errStr + nextErrStr)
              }
            }
          } getOrElse {
            logAndFail(s"""Unable to create new channel.
                          |Failed to update Channel meta-data.""".stripMargin)
          }
        }
      }
    } finally {
      events.close()
    }
  }

  def channelDelete(appName: String, deleteChannel: String): MaybeError = {
    val chanStorage = storage.Storage.getMetaDataChannels
    val events = storage.Storage.getLEvents()
    try {
      show(appName).right.flatMap { case (appDesc: AppDescription, channels: Seq[Channel]) =>
        val foundChannel = channels.find(ch => ch.name == deleteChannel)
        foundChannel match {
          case None =>
            logAndFail(s"""Unable to delete channel
                          |Channel ${deleteChannel} doesn't exists.""".stripMargin)
          case Some(channel) =>
            val dbRemoved = events.remove(appDesc.app.id, Some(channel.id))
            if (dbRemoved) {
              info(s"Removed Event Store for this channel: ${deleteChannel}")
              try {
                chanStorage.delete(channel.id)
                logAndSucceed(s"Deleted channel: ${deleteChannel}.")
              } catch {
                case e: Exception =>
                  logAndFail((s"""Unable to delete channel.
                   |Failed to update Channel meta-data.
                   |The channel ${deleteChannel} CANNOT be used!
                   |Please run 'pio app channel-delete ${appDesc.app.name} ${deleteChannel}'""" +
                    " to delete this channel again!").stripMargin)
              }
            } else {
              logAndFail(s"""Unable to delete channel.
                            |Error removing Event Store for this channel.""".stripMargin)
            }
        }
      }
    } finally {
      events.close()
    }
  }
}
