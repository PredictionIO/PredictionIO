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

import org.apache.predictionio.core.BuildInfo
import org.apache.predictionio.data.storage
import org.apache.predictionio.data.api.EventServer
import org.apache.predictionio.data.api.EventServerConfig
import org.apache.predictionio.tools.EventServerArgs
import org.apache.predictionio.tools.EitherLogging
import org.apache.predictionio.tools.Common
import org.apache.predictionio.tools.ReturnTypes._
import org.apache.predictionio.tools.dashboard.DashboardServer
import org.apache.predictionio.tools.dashboard.DashboardConfig
import org.apache.predictionio.tools.admin.AdminServer
import org.apache.predictionio.tools.admin.AdminServerConfig

import akka.actor.ActorSystem
import java.io.File
import scala.io.Source
import com.github.zafarkhaja.semver.Version

case class DashboardArgs(
  ip: String = "127.0.0.1",
  port: Int = 9000)

case class AdminServerArgs(
  ip: String = "127.0.0.1",
  port: Int = 7071)

case class PioStatus(
  version: String = "",
  pioHome: String = "",
  sparkHome: String = "",
  sparkVersion: String = "",
  sparkMinVersion: String = "",
  warnings: Seq[String] = Nil)

object Management extends EitherLogging {

  def version(): String = BuildInfo.version

  /** Starts a dashboard server and returns immediately
    *
    * @param da An instance of [[DashboardArgs]]
    * @return An instance of [[ActorSystem]] in which the server is being executed
    */
  def dashboard(da: DashboardArgs): ActorSystem = {
    info(s"Creating dashboard at ${da.ip}:${da.port}")
    DashboardServer.createDashboard(DashboardConfig(
      ip = da.ip,
      port = da.port))
  }

  /** Starts an eventserver server and returns immediately
    *
    * @param ea An instance of [[EventServerArgs]]
    * @return An instance of [[ActorSystem]] in which the server is being executed
    */
  def eventserver(ea: EventServerArgs): ActorSystem = {
    info(s"Creating Event Server at ${ea.ip}:${ea.port}")
    EventServer.createEventServer(EventServerConfig(
      ip = ea.ip,
      port = ea.port,
      stats = ea.stats))
  }

  /** Starts an adminserver server and returns immediately
    *
    * @param aa An instance of [[AdminServerArgs]]
    * @return An instance of [[ActorSystem]] in which the server is being executed
    */
  def adminserver(aa: AdminServerArgs): ActorSystem = {
    info(s"Creating Admin Server at ${aa.ip}:${aa.port}")
    AdminServer.createAdminServer(AdminServerConfig(
      ip = aa.ip,
      port = aa.port
    ))
  }

  private def stripMarginAndNewlines(string: String): String =
    string.stripMargin.replaceAll("\n", " ")

  def status(pioHome: Option[String], sparkHome: Option[String]): Expected[PioStatus] = {
    var pioStatus = PioStatus()
    info("Inspecting PredictionIO...")
    pioHome map { pioHome =>
      info(s"PredictionIO ${BuildInfo.version} is installed at $pioHome")
      pioStatus = pioStatus.copy(version = version(), pioHome = pioHome)
    } getOrElse {
      return logAndFail("Unable to locate PredictionIO installation. Aborting.")
    }
    info("Inspecting Apache Spark...")
    val sparkHomePath = Common.getSparkHome(sparkHome)
    if (new File(s"$sparkHomePath/bin/spark-submit").exists) {
      info(s"Apache Spark is installed at $sparkHomePath")
      val sparkMinVersion = "2.0.2"
      pioStatus = pioStatus.copy(
        sparkHome = sparkHomePath,
        sparkMinVersion = sparkMinVersion)
      val sparkReleaseFile = new File(s"$sparkHomePath/RELEASE")
      if (sparkReleaseFile.exists) {
        val sparkReleaseStrings =
          Source.fromFile(sparkReleaseFile).mkString.split(' ')
        if (sparkReleaseStrings.length < 2) {
          val warning = (stripMarginAndNewlines(
            s"""|Apache Spark version information cannot be found (RELEASE file
                |is empty). This is a known issue for certain vendors (e.g.
                |Cloudera). Please make sure you are using a version of at least
                |$sparkMinVersion."""))
          warn(warning)
          pioStatus = pioStatus.copy(warnings = pioStatus.warnings :+ warning)
        } else {
          val sparkReleaseVersion = sparkReleaseStrings(1)
          val parsedMinVersion = Version.valueOf(sparkMinVersion)
          val parsedCurrentVersion = Version.valueOf(sparkReleaseVersion)
          if (parsedCurrentVersion.greaterThanOrEqualTo(parsedMinVersion)) {
            info(stripMarginAndNewlines(
              s"""|Apache Spark $sparkReleaseVersion detected (meets minimum
                  |requirement of $sparkMinVersion)"""))
            pioStatus = pioStatus.copy(sparkVersion = sparkReleaseVersion)
          } else {
            return logAndFail(stripMarginAndNewlines(
              s"""|Apache Spark $sparkReleaseVersion detected (does not meet
                  |minimum requirement. Aborting."""))
          }
        }
      } else {
        val warning = (stripMarginAndNewlines(
          s"""|Apache Spark version information cannot be found. If you are
              |using a developmental tree, please make sure you are using a
              |version of at least $sparkMinVersion."""))
        warn(warning)
        pioStatus = pioStatus.copy(warnings = pioStatus.warnings :+ warning)
      }
    } else {
      return logAndFail("Unable to locate a proper Apache Spark installation. Aborting.")
    }
    info("Inspecting storage backend connections...")
    try {
      storage.Storage.verifyAllDataObjects()
    } catch {
      case e: Throwable =>
        val errStr = s"""Unable to connect to all storage backends successfully.
            |The following shows the error message from the storage backend.
            |
            |${e.getMessage} (${e.getClass.getName})
            |
            |Dumping configuration of initialized storage backend sources.
            |Please make sure they are correct.
            |
            |""".stripMargin
        val sources = storage.Storage.config.get("sources") map { src =>
          src map { case (s, p) =>
            s"Source Name: $s; Type: ${p.getOrElse("type", "(error)")}; " +
              s"Configuration: ${p.getOrElse("config", "(error)")}"
          } mkString("\n")
        } getOrElse {
          "No properly configured storage backend sources."
        }
        return logOnFail(errStr + sources, e)
    }
    info("Your system is all ready to go.")
    Right(pioStatus)
  }
}
