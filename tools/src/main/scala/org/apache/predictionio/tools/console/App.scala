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

case class AppArgs(
  id: Option[Int] = None,
  name: String = "",
  channel: String = "",
  dataDeleteChannel: Option[String] = None,
  all: Boolean = false,
  force: Boolean = false,
  description: Option[String] = None)

object App extends Logging {
  def create(ca: ConsoleArgs): Int = {
    val apps = storage.Storage.getMetaDataApps()
    // get the client in the beginning so error exit right away if can't access client
    val events = storage.Storage.getLEvents()
    apps.getByName(ca.app.name) map { app =>
      error(s"App ${ca.app.name} already exists. Aborting.")
      1
    } getOrElse {
      ca.app.id.map { id =>
        apps.get(id) map { app =>
          error(
            s"App ID ${id} already exists and maps to the app '${app.name}'. " +
            "Aborting.")
          return 1
        }
      }
      val appid = apps.insert(storage.App(
        id = ca.app.id.getOrElse(0),
        name = ca.app.name,
        description = ca.app.description))
      appid map { id =>
        val dbInit = events.init(id)
        val r = if (dbInit) {
          info(s"Initialized Event Store for this app ID: ${id}.")
          val accessKeys = storage.Storage.getMetaDataAccessKeys
          val accessKey = accessKeys.insert(storage.AccessKey(
            key = ca.accessKey.accessKey,
            appid = id,
            events = Seq()))
          accessKey map { k =>
            info("Created new app:")
            info(s"      Name: ${ca.app.name}")
            info(s"        ID: ${id}")
            info(s"Access Key: ${k}")
            0
          } getOrElse {
            error(s"Unable to create new access key.")
            1
          }
        } else {
          error(s"Unable to initialize Event Store for this app ID: ${id}.")
          // revert back the meta data change
          try {
            apps.delete(id)
            0
          } catch {
            case e: Exception =>
              error(s"Failed to revert back the App meta-data change.", e)
              error(s"The app ${ca.app.name} CANNOT be used!")
              error(s"Please run 'pio app delete ${ca.app.name}' " +
                "to delete this app!")
              1
          }
        }
        events.close()
        r
      } getOrElse {
        error(s"Unable to create new app.")
        1
      }
    }
  }

  def list(ca: ConsoleArgs): Int = {
    val apps = storage.Storage.getMetaDataApps.getAll().sortBy(_.name)
    val accessKeys = storage.Storage.getMetaDataAccessKeys
    val title = "Name"
    val ak = "Access Key"
    info(f"$title%20s |   ID | $ak%64s | Allowed Event(s)")
    apps foreach { app =>
      val keys = accessKeys.getByAppid(app.id)
      keys foreach { k =>
        val events =
          if (k.events.size > 0) k.events.sorted.mkString(",") else "(all)"
        info(f"${app.name}%20s | ${app.id}%4d | ${k.key}%64s | $events%s")
      }
    }
    info(s"Finished listing ${apps.size} app(s).")
    0
  }

  def show(ca: ConsoleArgs): Int = {
    val apps = storage.Storage.getMetaDataApps
    val accessKeys = storage.Storage.getMetaDataAccessKeys
    val channels = storage.Storage.getMetaDataChannels
    apps.getByName(ca.app.name) map { app =>
      info(s"    App Name: ${app.name}")
      info(s"      App ID: ${app.id}")
      info(s" Description: ${app.description.getOrElse("")}")
      val keys = accessKeys.getByAppid(app.id)

      var firstKey = true
      keys foreach { k =>
        val events =
          if (k.events.size > 0) k.events.sorted.mkString(",") else "(all)"
        if (firstKey) {
          info(f"  Access Key: ${k.key}%s | ${events}%s")
          firstKey = false
        } else {
          info(f"              ${k.key}%s | ${events}%s")
        }
      }

      val chans = channels.getByAppid(app.id)
      var firstChan = true
      val titleName = "Channel Name"
      val titleID = "Channel ID"
      chans.foreach { ch =>
        if (firstChan) {
          info(f"    Channels: ${titleName}%16s | ${titleID}%10s ")
          firstChan = false
        }
        info(f"              ${ch.name}%16s | ${ch.id}%10s")
      }
      0
    } getOrElse {
      error(s"App ${ca.app.name} does not exist. Aborting.")
      1
    }
  }

  def delete(ca: ConsoleArgs): Int = {
    val apps = storage.Storage.getMetaDataApps
    val accesskeys = storage.Storage.getMetaDataAccessKeys
    val channels = storage.Storage.getMetaDataChannels
    val events = storage.Storage.getLEvents()
    val status = apps.getByName(ca.app.name) map { app =>
      info(s"The following app (including all channels) will be deleted. Are you sure?")
      info(s"    App Name: ${app.name}")
      info(s"      App ID: ${app.id}")
      info(s" Description: ${app.description.getOrElse("")}")
      val chans = channels.getByAppid(app.id)
      var firstChan = true
      val titleName = "Channel Name"
      val titleID = "Channel ID"
      chans.foreach { ch =>
        if (firstChan) {
          info(f"    Channels: ${titleName}%16s | ${titleID}%10s ")
          firstChan = false
        }
        info(f"              ${ch.name}%16s | ${ch.id}%10s")
      }

      val choice = if(ca.app.force) "YES" else readLine("Enter 'YES' to proceed: ")
      choice match {
        case "YES" => {
          // delete channels
          val delChannelStatus: Seq[Int] = chans.map { ch =>
            if (events.remove(app.id, Some(ch.id))) {
              info(s"Removed Event Store of the channel ID: ${ch.id}")
              try {
                channels.delete(ch.id)
                info(s"Deleted channel ${ch.name}")
                0
              } catch {
                case e: Exception =>
                  error(s"Error deleting channel ${ch.name}.", e)
                  1
              }
            } else {
              error(s"Error removing Event Store of the channel ID: ${ch.id}.")
              return 1
            }
          }

          if (delChannelStatus.exists(_ != 0)) {
            error("Error occurred while deleting channels. Aborting.")
            return 1
          }

          try {
            events.remove(app.id)
            info(s"Removed Event Store for this app ID: ${app.id}")
          } catch {
            case e: Exception =>
              error(s"Error removing Event Store for this app. Aborting.", e)
              return 1
          }

          accesskeys.getByAppid(app.id) foreach { key =>
            try {
              accesskeys.delete(key.key)
              info(s"Removed access key ${key.key}")
            } catch {
              case e: Exception =>
                error(s"Error removing access key ${key.key}. Aborting.", e)
                return 1
            }
          }

          try {
            apps.delete(app.id)
            info(s"Deleted app ${app.name}.")
          } catch {
            case e: Exception =>
              error(s"Error deleting app ${app.name}. Aborting.", e)
              return 1
          }

          info("Done.")
          0
        }
        case _ =>
          info("Aborted.")
          0
      }
    } getOrElse {
      error(s"App ${ca.app.name} does not exist. Aborting.")
      1
    }
    events.close()
    status
  }

  def dataDelete(ca: ConsoleArgs): Int = {
    if (ca.app.all) {
      dataDeleteAll(ca)
    } else {
      dataDeleteOne(ca)
    }
  }

  def dataDeleteOne(ca: ConsoleArgs): Int = {
    val apps = storage.Storage.getMetaDataApps
    val channels = storage.Storage.getMetaDataChannels
    apps.getByName(ca.app.name) map { app =>

      val channelId = ca.app.dataDeleteChannel.map { ch =>
        val channelMap = channels.getByAppid(app.id).map(c => (c.name, c.id)).toMap
        if (!channelMap.contains(ch)) {
          error(s"Unable to delete data for channel.")
          error(s"Channel ${ch} doesn't exist.")
          return 1
        }

        channelMap(ch)
      }

      if (channelId.isDefined) {
        info(s"Data of the following channel will be deleted. Are you sure?")
        info(s"Channel Name: ${ca.app.dataDeleteChannel.get}")
        info(s"  Channel ID: ${channelId.get}")
        info(s"    App Name: ${app.name}")
        info(s"      App ID: ${app.id}")
        info(s" Description: ${app.description}")
      } else {
        info(s"Data of the following app (default channel only) will be deleted. Are you sure?")
        info(s"    App Name: ${app.name}")
        info(s"      App ID: ${app.id}")
        info(s" Description: ${app.description}")
      }

      val choice = if(ca.app.force) "YES" else readLine("Enter 'YES' to proceed: ")

      choice match {
        case "YES" => {
          val events = storage.Storage.getLEvents()
          // remove table
          val r1 = if (events.remove(app.id, channelId)) {
            if (channelId.isDefined) {
              info(s"Removed Event Store for this channel ID: ${channelId.get}")
            } else {
              info(s"Removed Event Store for this app ID: ${app.id}")
            }
            0
          } else {
            if (channelId.isDefined) {
              error(s"Error removing Event Store for this channel.")
            } else {
              error(s"Error removing Event Store for this app.")
            }
            1
          }
          // re-create table
          val dbInit = events.init(app.id, channelId)
          val r2 = if (dbInit) {
            if (channelId.isDefined) {
              info(s"Initialized Event Store for this channel ID: ${channelId.get}.")
            } else {
              info(s"Initialized Event Store for this app ID: ${app.id}.")
            }
            0
          } else {
            if (channelId.isDefined) {
              error(s"Unable to initialize Event Store for this channel ID:" +
                s" ${channelId.get}.")
            } else {
              error(s"Unable to initialize Event Store for this appId:" +
                s" ${app.id}.")
            }
            1
          }
          events.close()
          info("Done.")
          r1 + r2
        }
        case _ =>
          info("Aborted.")
          0
      }
    } getOrElse {
      error(s"App ${ca.app.name} does not exist. Aborting.")
      1
    }
  }

  def dataDeleteAll(ca: ConsoleArgs): Int = {
    val apps = storage.Storage.getMetaDataApps
    val channels = storage.Storage.getMetaDataChannels
    val events = storage.Storage.getLEvents()
    val status = apps.getByName(ca.app.name) map { app =>
      info(s"All data of the app (including default and all channels) will be deleted." +
        " Are you sure?")
      info(s"    App Name: ${app.name}")
      info(s"      App ID: ${app.id}")
      info(s" Description: ${app.description}")
      val chans = channels.getByAppid(app.id)
      var firstChan = true
      val titleName = "Channel Name"
      val titleID = "Channel ID"
      chans.foreach { ch =>
        if (firstChan) {
          info(f"    Channels: ${titleName}%16s | ${titleID}%10s ")
          firstChan = false
        }
        info(f"              ${ch.name}%16s | ${ch.id}%10s")
      }

      val choice = if(ca.app.force) "YES" else readLine("Enter 'YES' to proceed: ")
      choice match {
        case "YES" => {
          // delete channels
          val delChannelStatus: Seq[Int] = chans.map { ch =>
            val r1 = if (events.remove(app.id, Some(ch.id))) {
              info(s"Removed Event Store of the channel ID: ${ch.id}")
              0
            } else {
              error(s"Error removing Event Store of the channel ID: ${ch.id}.")
              1
            }
            // re-create table
            val dbInit = events.init(app.id, Some(ch.id))
            val r2 = if (dbInit) {
              info(s"Initialized Event Store of the channel ID: ${ch.id}")
              0
            } else {
              error(s"Unable to initialize Event Store of the channel ID: ${ch.id}.")
              1
            }
            r1 + r2
          }

          if (delChannelStatus.filter(_ != 0).isEmpty) {
            val r1 = if (events.remove(app.id)) {
              info(s"Removed Event Store for this app ID: ${app.id}")
              0
            } else {
              error(s"Error removing Event Store for this app.")
              1
            }

            val dbInit = events.init(app.id)
            val r2 = if (dbInit) {
              info(s"Initialized Event Store for this app ID: ${app.id}.")
              0
            } else {
              error(s"Unable to initialize Event Store for this appId: ${app.id}.")
              1
            }
            info("Done.")
            r1 + r2
          } else 1
        }
        case _ =>
          info("Aborted.")
          0
      }
    } getOrElse {
      error(s"App ${ca.app.name} does not exist. Aborting.")
      1
    }
    events.close()
    status
  }

  def channelNew(ca: ConsoleArgs): Int = {
    val apps = storage.Storage.getMetaDataApps
    val channels = storage.Storage.getMetaDataChannels
    val events = storage.Storage.getLEvents()
    val newChannel = ca.app.channel
    val status = apps.getByName(ca.app.name) map { app =>
      val channelMap = channels.getByAppid(app.id).map(c => (c.name, c.id)).toMap
      if (channelMap.contains(newChannel)) {
        error(s"Unable to create new channel.")
        error(s"Channel ${newChannel} already exists.")
        1
      } else if (!storage.Channel.isValidName(newChannel)) {
        error(s"Unable to create new channel.")
        error(s"The channel name ${newChannel} is invalid.")
        error(s"${storage.Channel.nameConstraint}")
        1
      } else {

        val channelId = channels.insert(storage.Channel(
          id = 0, // new id will be assigned
          appid = app.id,
          name = newChannel
        ))
        channelId.map { chanId =>
          info(s"Updated Channel meta-data.")
          // initialize storage
          val dbInit = events.init(app.id, Some(chanId))
          if (dbInit) {
            info(s"Initialized Event Store for the channel: ${newChannel}.")
            info(s"Created new channel:")
            info(s"    Channel Name: ${newChannel}")
            info(s"      Channel ID: ${chanId}")
            info(s"          App ID: ${app.id}")
            0
          } else {
            error(s"Unable to create new channel.")
            error(s"Failed to initalize Event Store.")
            // reverted back the meta data
            try {
              channels.delete(chanId)
              0
            } catch {
              case e: Exception =>
                error(s"Failed to revert back the Channel meta-data change.", e)
                error(s"The channel ${newChannel} CANNOT be used!")
                error(s"Please run 'pio app channel-delete ${app.name} ${newChannel}' " +
                  "to delete this channel!")
                1
            }
          }
        }.getOrElse {
          error(s"Unable to create new channel.")
          error(s"Failed to update Channel meta-data.")
          1
        }
      }
    } getOrElse {
      error(s"App ${ca.app.name} does not exist. Aborting.")
      1
    }
    events.close()
    status
  }

  def channelDelete(ca: ConsoleArgs): Int = {
    val apps = storage.Storage.getMetaDataApps
    val channels = storage.Storage.getMetaDataChannels
    val events = storage.Storage.getLEvents()
    val deleteChannel = ca.app.channel
    val status = apps.getByName(ca.app.name) map { app =>
      val channelMap = channels.getByAppid(app.id).map(c => (c.name, c.id)).toMap
      if (!channelMap.contains(deleteChannel)) {
        error(s"Unable to delete channel.")
        error(s"Channel ${deleteChannel} doesn't exist.")
        1
      } else {
        info(s"The following channel will be deleted. Are you sure?")
        info(s"    Channel Name: ${deleteChannel}")
        info(s"      Channel ID: ${channelMap(deleteChannel)}")
        info(s"        App Name: ${app.name}")
        info(s"          App ID: ${app.id}")
        val choice = if(ca.app.force) "YES" else readLine("Enter 'YES' to proceed: ")
        choice match {
          case "YES" => {
            // NOTE: remove storage first before remove meta data (in case remove storage failed)
            val dbRemoved = events.remove(app.id, Some(channelMap(deleteChannel)))
            if (dbRemoved) {
              info(s"Removed Event Store for this channel: ${deleteChannel}")
              try {
                channels.delete(channelMap(deleteChannel))
                info(s"Deleted channel: ${deleteChannel}.")
                0
              } catch {
                case e: Exception =>
                  error(s"Unable to delete channel.", e)
                  error(s"Failed to update Channel meta-data.")
                  error(s"The channel ${deleteChannel} CANNOT be used!")
                  error(s"Please run 'pio app channel-delete ${app.name} ${deleteChannel}' " +
                    "to delete this channel again!")
                  1
              }
            } else {
              error(s"Unable to delete channel.")
              error(s"Error removing Event Store for this channel.")
              1
            }
          }
          case _ =>
            info("Aborted.")
            0
        }
      }
    } getOrElse {
      error(s"App ${ca.app.name} does not exist. Aborting.")
      1
    }
    events.close()
    status
  }

}
