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

package org.apache.predictionio.tools.console

import org.apache.predictionio.tools.{BatchPredictArgs, DeployArgs, EventServerArgs, ServerArgs, SparkArgs, WorkflowArgs}
import org.apache.predictionio.tools.commands.{AdminServerArgs, BuildArgs, DashboardArgs, Engine, EngineArgs, Export, ExportArgs, Import, ImportArgs, Management, AccessKey => AccessKeysCmd, App => AppCmd}
import org.apache.predictionio.tools.ReturnTypes._
import grizzled.slf4j.Logging

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.language.implicitConversions
import scala.sys.process._

object Pio extends Logging {

  private implicit def eitherToInt[A, B](result: Either[A, B]): Int = {
    result fold (_ => 1, _ => 0)
  }

  private def doOnSuccess[A, B](result: Either[A, B])(f: B => Int): Int = {
    result match {
      case Left(_) => 1
      case Right(res) => f(res)
    }
  }

  private def processAwaitAndClean(maybeProc: Expected[(Process, () => Unit)]) = {
    maybeProc match {
      case Left(_) => 1

      case Right((proc, cleanup)) =>
        Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
          def run(): Unit = {
            cleanup()
            proc.destroy()
          }
        }))
        val returnVal = proc.exitValue()
        cleanup()
        returnVal
    }
  }

  def version(): Int = {
    println(Management.version)
    0
  }

  def build(
    ea: EngineArgs,
    buildArgs: BuildArgs,
    pioHome: String,
    verbose: Boolean = false): Int = {

    doOnSuccess(Engine.build(ea, buildArgs, pioHome, verbose)) {
      _ => info("Your engine is ready for training.")
      0
    }
  }

  def train(
    ea: EngineArgs,
    wa: WorkflowArgs,
    sa: SparkArgs,
    pioHome: String,
    verbose: Boolean = false): Int =
      processAwaitAndClean(Engine.train(ea, wa, sa, pioHome, verbose))

  def eval(
    ea: EngineArgs,
    wa: WorkflowArgs,
    sa: SparkArgs,
    pioHome: String,
    verbose: Boolean = false): Int =
      processAwaitAndClean(Engine.train(ea, wa, sa, pioHome, verbose))

  def deploy(
    ea: EngineArgs,
    engineInstanceId: Option[String],
    serverArgs: ServerArgs,
    sparkArgs: SparkArgs,
    pioHome: String,
    verbose: Boolean = false): Int =
      processAwaitAndClean(Engine.deploy(
        ea, engineInstanceId, serverArgs, sparkArgs, pioHome, verbose))

  def undeploy(da: DeployArgs): Int = Engine.undeploy(da)

  def batchPredict(
    ea: EngineArgs,
    engineInstanceId: Option[String],
    batchPredictArgs: BatchPredictArgs,
    sparkArgs: SparkArgs,
    pioHome: String,
    verbose: Boolean = false): Int =
      processAwaitAndClean(Engine.batchPredict(
        ea, engineInstanceId, batchPredictArgs, sparkArgs, pioHome, verbose))

  def dashboard(da: DashboardArgs): Int = {
    Await.ready(Management.dashboard(da).whenTerminated, Duration.Inf)
    0
  }

  def eventserver(ea: EventServerArgs): Int = {
    Await.ready(Management.eventserver(ea).whenTerminated, Duration.Inf)
    0
  }

  def adminserver(aa: AdminServerArgs): Int = {
    Await.ready(Management.adminserver(aa).whenTerminated, Duration.Inf)
    0
  }

  def run(
    ea: EngineArgs,
    mainClass: String,
    driverArguments: Seq[String],
    buildArgs: BuildArgs,
    sparkArgs: SparkArgs,
    pioHome: String,
    verbose: Boolean = false): Int =
      doOnSuccess(Engine.run(
        ea, mainClass, driverArguments, buildArgs,
        sparkArgs, pioHome, verbose)) { proc =>

          val r = proc.exitValue()
          if (r != 0) {
            error(s"Return code of previous step is ${r}. Aborting.")
            return 1
          }
          r
        }


  def status(pioHome: Option[String], sparkHome: Option[String]): Int = {
    Management.status(pioHome, sparkHome)
  }

  def imprt(ia: ImportArgs, sa: SparkArgs, pioHome: String): Int = {
    processAwaitAndClean(Import.fileToEvents(ia, sa, pioHome))
  }

  def export(ea: ExportArgs, sa: SparkArgs, pioHome: String): Int = {
    processAwaitAndClean(Export.eventsToFile(ea, sa, pioHome))
  }

  object App {

    def create(
      name: String,
      id: Option[Int] = None,
      description: Option[String] = None,
      accessKey: String = ""): Int =
        doOnSuccess(AppCmd.create(name, id, description, accessKey)) { appDesc =>
            info("Created a new app:")
            info(s"      Name: ${appDesc.app.name}")
            info(s"        ID: ${appDesc.app.id}")
            info(s"Access Key: ${appDesc.keys.head.key}")
            0
        }

    def list(): Int = {
      val title = "Name"
      val ak = "Access Key"
      val apps = AppCmd.list
      info(f"$title%20s |   ID | $ak%64s | Allowed Event(s)")
      apps foreach { appDesc =>
        appDesc.keys foreach { k =>
          val events =
            if (k.events.size > 0) k.events.sorted.mkString(",") else "(all)"
          info(f"${appDesc.app.name}%20s | ${appDesc.app.id}%4d | ${k.key}%64s | $events%s")
        }
      }
      info(s"Finished listing ${apps.size} app(s).")
      0
    }

    def show(appName: String): Int =
      doOnSuccess(AppCmd.show(appName)) { case (appDesc, chans) =>
        info(s"    App Name: ${appDesc.app.name}")
        info(s"      App ID: ${appDesc.app.id}")
        info(s" Description: ${appDesc.app.description.getOrElse("")}")

        var firstKey = true
        appDesc.keys foreach { k =>
          val events =
            if (k.events.size > 0) k.events.sorted.mkString(",") else "(all)"
          if (firstKey) {
            info(f"  Access Key: ${k.key}%s | ${events}%s")
            firstKey = false
          } else {
            info(f"              ${k.key}%s | ${events}%s")
          }
        }
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
      }

    def delete(name: String, force: Boolean = false): Int =
      doOnSuccess(AppCmd.show(name)) { case (appDesc, chans) =>
        info(s"The following app (including all channels) will be deleted. Are you sure?")
        info(s"    App Name: ${appDesc.app.name}")
        info(s"      App ID: ${appDesc.app.id}")
        info(s" Description: ${appDesc.app.description.getOrElse("")}")
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

        val choice = if(force) "YES" else readLine("Enter 'YES' to proceed: ")
        choice match {
          case "YES" =>
            AppCmd.delete(name)
          case _ =>
            info("Aborted.")
            0
        }
      }

    def dataDelete(
      name: String,
      channel: Option[String] = None,
      all: Boolean = false,
      force: Boolean = false): Int =
        doOnSuccess(AppCmd.show(name)) { case (appDesc, chans) =>

          val channelId = channel.map { ch =>
            val channelMap = chans.map(c => (c.name, c.id)).toMap
            if (!channelMap.contains(ch)) {
              error(s"Unable to delete data for channel.")
              error(s"Channel ${ch} doesn't exist.")
              return 1
            }
            channelMap(ch)
          }
          if (all) {
            info(s"All data of the app (including default and all channels) will be deleted." +
              " Are you sure?")
          } else if (channelId.isDefined) {
            info(s"Data of the following channel will be deleted. Are you sure?")
            info(s"Channel Name: ${channel.get}")
            info(s"  Channel ID: ${channelId.get}")
          } else {
            info(s"Data of the following app (default channel only) will be deleted. Are you sure?")
          }
          info(s"    App Name: ${appDesc.app.name}")
          info(s"      App ID: ${appDesc.app.id}")
          info(s" Description: ${appDesc.app.description}")

          val choice = if(force) "YES" else readLine("Enter 'YES' to proceed: ")
          choice match {
            case "YES" =>
              AppCmd.dataDelete(name, channel, all)
            case _ =>
              info("Aborted.")
              0
          }
        }

    def channelNew(appName: String, newChannel: String): Int =
      AppCmd.channelNew(appName, newChannel)

    def channelDelete(
      appName: String,
      deleteChannel: String,
      force: Boolean = false): Int =
        doOnSuccess(AppCmd.show(appName)) { case (appDesc, chans) =>
          chans.find(chan => chan.name == deleteChannel) match {
            case None =>
              error(s"Unable to delete channel.")
              error(s"Channel ${deleteChannel} doesn't exist.")
              1
            case Some(chan) =>
              info(s"The following channel will be deleted. Are you sure?")
              info(s"    Channel Name: ${deleteChannel}")
              info(s"      Channel ID: ${chan.id}")
              info(s"        App Name: ${appDesc.app.name}")
              info(s"          App ID: ${appDesc.app.id}")
              val choice = if(force) "YES" else readLine("Enter 'YES' to proceed: ")
              choice match {
                case "YES" =>
                  AppCmd.channelDelete(appName, deleteChannel)
                case _ =>
                  info("Aborted.")
                  0
              }
          }
        }

  }

  object AccessKey {

    def create(
      appName: String,
      key: String,
      events: Seq[String]): Int =
        AccessKeysCmd.create(appName, key, events)

    def list(app: Option[String]): Int =
      doOnSuccess(AccessKeysCmd.list(app)) { keys =>
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

    def delete(key: String): Int = AccessKeysCmd.delete(key)
  }

}

