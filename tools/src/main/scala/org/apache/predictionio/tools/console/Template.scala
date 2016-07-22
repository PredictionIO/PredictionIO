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

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.ConnectException
import java.net.URI
import java.util.zip.ZipInputStream

import grizzled.slf4j.Logging
import org.apache.predictionio.controller.Utils
import org.apache.predictionio.core.BuildInfo
import org.apache.commons.io.FileUtils
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.read
import org.json4s.native.Serialization.write
import semverfi._

import scala.io.Source
import scala.sys.process._
import scalaj.http._

case class TemplateArgs(
  directory: String = "",
  repository: String = "",
  version: Option[String] = None,
  name: Option[String] = None,
  packageName: Option[String] = None,
  email: Option[String] = None)

case class GitHubTag(
  name: String,
  zipball_url: String,
  tarball_url: String,
  commit: GitHubCommit)

case class GitHubCommit(
  sha: String,
  url: String)

case class GitHubCache(
  headers: Map[String, String],
  body: String)

case class TemplateEntry(
  repo: String)

case class TemplateMetaData(
  pioVersionMin: Option[String] = None)

object Template extends Logging {
  implicit val formats = Utils.json4sDefaultFormats

  def templateMetaData(templateJson: File): TemplateMetaData = {
    if (!templateJson.exists) {
      warn(s"$templateJson does not exist. Template metadata will not be available. " +
        "(This is safe to ignore if you are not working on a template.)")
      TemplateMetaData()
    } else {
      val jsonString = Source.fromFile(templateJson)(scala.io.Codec.ISO8859).mkString
      val json = try {
        parse(jsonString)
      } catch {
        case e: org.json4s.ParserUtil.ParseException =>
          warn(s"$templateJson cannot be parsed. Template metadata will not be available.")
          return TemplateMetaData()
      }
      val pioVersionMin = json \ "pio" \ "version" \ "min"
      pioVersionMin match {
        case JString(s) => TemplateMetaData(pioVersionMin = Some(s))
        case _ => TemplateMetaData()
      }
    }
  }

  /** Creates a wrapper that provides the functionality of scalaj.http.Http()
    * with automatic proxy settings handling. The proxy settings will first
    * come from "git" followed by system properties "http.proxyHost" and
    * "http.proxyPort".
    *
    * @param url URL to be connected
    * @return
    */
  def httpOptionalProxy(url: String): HttpRequest = {
    val gitProxy = try {
      Some(Process("git config --global http.proxy").lines.toList(0))
    } catch {
      case e: Throwable => None
    }

    val (host, port) = gitProxy map { p =>
      val proxyUri = new URI(p)
      (Option(proxyUri.getHost),
        if (proxyUri.getPort == -1) None else Some(proxyUri.getPort))
    } getOrElse {
      (sys.props.get("http.proxyHost"),
        sys.props.get("http.proxyPort").map { p =>
          try {
            Some(p.toInt)
          } catch {
            case e: NumberFormatException => None
          }
        } getOrElse None)
    }

    (host, port) match {
      case (Some(h), Some(p)) => Http(url).proxy(h, p)
      case _ => Http(url)
    }
  }

  def getGitHubRepos(
      repos: Seq[String],
      apiType: String,
      repoFilename: String): Map[String, GitHubCache] = {
    val reposCache = try {
      val cache =
        Source.fromFile(repoFilename)(scala.io.Codec.ISO8859).mkString
        read[Map[String, GitHubCache]](cache)
    } catch {
      case e: Throwable => Map[String, GitHubCache]()
    }
    val newReposCache = reposCache ++ (try {
      repos.map { repo =>
        val url = s"https://api.github.com/repos/$repo/$apiType"
        val http = httpOptionalProxy(url)
        val response = reposCache.get(repo).map { cache =>
          cache.headers.get("ETag").map { etag =>
            http.header("If-None-Match", etag).asString
          } getOrElse {
            http.asString
          }
        } getOrElse {
          http.asString
        }

        val body = if (response.code == 304) {
          reposCache(repo).body
        } else {
          response.body
        }

        repo -> GitHubCache(headers = response.headers, body = body)
      }.toMap
    } catch {
      case e: ConnectException =>
        githubConnectErrorMessage(e)
        Map()
    })
    FileUtils.writeStringToFile(
      new File(repoFilename),
      write(newReposCache),
      "ISO-8859-1")
    newReposCache
  }

  def sub(repo: String, name: String, email: String, org: String): Unit = {
    val data = Map(
      "repo" -> repo,
      "name" -> name,
      "email" -> email,
      "org" -> org)
    try {
      httpOptionalProxy("https://update.prediction.io/templates.subscribe").
        postData("json=" + write(data)).asString
    } catch {
      case e: Throwable => error("Unable to subscribe.")
    }
  }

  def meta(repo: String, name: String, org: String): Unit = {
    try {
      httpOptionalProxy(
        s"https://meta.prediction.io/templates/$repo/$org/$name").asString
    } catch {
      case e: Throwable => debug("Template metadata unavailable.")
    }
  }

  def list(ca: ConsoleArgs): Int = {
    val templatesUrl = "https://templates.prediction.io/index.json"
    try {
      val templatesJson = Source.fromURL(templatesUrl).mkString("")
      val templates = read[List[TemplateEntry]](templatesJson)
      println("The following is a list of template IDs registered on " +
        "PredictionIO Template Gallery:")
      println()
      templates.sortBy(_.repo.toLowerCase).foreach { template =>
        println(template.repo)
      }
      println()
      println("Notice that it is possible use any GitHub repository as your " +
        "engine template ID (e.g. YourOrg/YourTemplate).")
      0
    } catch {
      case e: Throwable =>
        error(s"Unable to list templates from $templatesUrl " +
          s"(${e.getMessage}). Aborting.")
        1
    }
  }

  def githubConnectErrorMessage(e: ConnectException): Unit = {
    error(s"Unable to connect to GitHub (Reason: ${e.getMessage}). " +
      "Please check your network configuration and proxy settings.")
  }

  def get(ca: ConsoleArgs): Int = {
    val repos =
      getGitHubRepos(Seq(ca.template.repository), "tags", ".templates-cache")

    repos.get(ca.template.repository).map { repo =>
      try {
        read[List[GitHubTag]](repo.body)
      } catch {
        case e: MappingException =>
          error(s"Either ${ca.template.repository} is not a valid GitHub " +
            "repository, or it does not have any tag. Aborting.")
          return 1
      }
    } getOrElse {
      error(s"Failed to retrieve ${ca.template.repository}. Aborting.")
      return 1
    }

    val name = ca.template.name getOrElse {
      try {
        Process("git config --global user.name").lines.toList(0)
      } catch {
        case e: Throwable =>
          readLine("Please enter author's name: ")
      }
    }

    val organization = ca.template.packageName getOrElse {
      readLine(
        "Please enter the template's Scala package name (e.g. com.mycompany): ")
    }

    val email = ca.template.email getOrElse {
      try {
        Process("git config --global user.email").lines.toList(0)
      } catch {
        case e: Throwable =>
          readLine("Please enter author's e-mail address: ")
      }
    }

    println(s"Author's name:         $name")
    println(s"Author's e-mail:       $email")
    println(s"Author's organization: $organization")

    var subscribe = readLine("Would you like to be informed about new bug " +
      "fixes and security updates of this template? (Y/n) ")
    var valid = false

    do {
      subscribe match {
        case "" | "Y" | "y" =>
          sub(ca.template.repository, name, email, organization)
          valid = true
        case "n" | "N" =>
          meta(ca.template.repository, name, organization)
          valid = true
        case _ =>
          println("Please answer 'y' or 'n'")
          subscribe = readLine("(Y/n)? ")
      }
    } while (!valid)

    val repo = repos(ca.template.repository)

    println(s"Retrieving ${ca.template.repository}")
    val tags = read[List[GitHubTag]](repo.body)
    println(s"There are ${tags.size} tags")

    if (tags.size == 0) {
      println(s"${ca.template.repository} does not have any tag. Aborting.")
      return 1
    }

    val tag = ca.template.version.map { v =>
      tags.find(_.name == v).getOrElse {
        println(s"${ca.template.repository} does not have tag $v. Aborting.")
        return 1
      }
    } getOrElse tags.head

    println(s"Using tag ${tag.name}")
    val url =
      s"https://github.com/${ca.template.repository}/archive/${tag.name}.zip"
    println(s"Going to download $url")
    val trial = try {
      httpOptionalProxy(url).asBytes
    } catch {
      case e: ConnectException =>
        githubConnectErrorMessage(e)
        return 1
    }
    val finalTrial = try {
      trial.location.map { loc =>
        println(s"Redirecting to $loc")
        httpOptionalProxy(loc).asBytes
      } getOrElse trial
    } catch {
      case e: ConnectException =>
        githubConnectErrorMessage(e)
        return 1
    }
    val zipFilename =
      s"${ca.template.repository.replace('/', '-')}-${tag.name}.zip"
    FileUtils.writeByteArrayToFile(
      new File(zipFilename),
      finalTrial.body)
    val zis = new ZipInputStream(
      new BufferedInputStream(new FileInputStream(zipFilename)))
    val bufferSize = 4096
    val filesToModify = collection.mutable.ListBuffer[String]()
    var ze = zis.getNextEntry
    while (ze != null) {
      val filenameSegments = ze.getName.split(File.separatorChar)
      val destFilename = (ca.template.directory +: filenameSegments.tail).
        mkString(File.separator)
      if (ze.isDirectory) {
        new File(destFilename).mkdirs
      } else {
        val os = new BufferedOutputStream(
          new FileOutputStream(destFilename),
          bufferSize)
        val data = Array.ofDim[Byte](bufferSize)
        var count = zis.read(data, 0, bufferSize)
        while (count != -1) {
          os.write(data, 0, count)
          count = zis.read(data, 0, bufferSize)
        }
        os.flush()
        os.close()

        val nameOnly = new File(destFilename).getName

        if (organization != "" &&
          (nameOnly.endsWith(".scala") ||
            nameOnly == "build.sbt" ||
            nameOnly == "engine.json")) {
          filesToModify += destFilename
        }
      }
      ze = zis.getNextEntry
    }
    zis.close()
    new File(zipFilename).delete

    val engineJsonFile =
      new File(ca.template.directory, "engine.json")

    val engineJson = try {
      Some(parse(Source.fromFile(engineJsonFile).mkString))
    } catch {
      case e: java.io.IOException =>
        error("Unable to read engine.json. Skipping automatic package " +
          "name replacement.")
        None
      case e: MappingException =>
        error("Unable to parse engine.json. Skipping automatic package " +
          "name replacement.")
        None
    }

    val engineFactory = engineJson.map { ej =>
      (ej \ "engineFactory").extractOpt[String]
    } getOrElse None

    engineFactory.map { ef =>
      val pkgName = ef.split('.').dropRight(1).mkString(".")
      println(s"Replacing $pkgName with $organization...")

      filesToModify.foreach { ftm =>
        println(s"Processing $ftm...")
        val fileContent = Source.fromFile(ftm).getLines()
        val processedLines =
          fileContent.map(_.replaceAllLiterally(pkgName, organization))
        FileUtils.writeStringToFile(
          new File(ftm),
          processedLines.mkString("\n"))
      }
    } getOrElse {
      error("engineFactory is not found in engine.json. Skipping automatic " +
        "package name replacement.")
    }

    verifyTemplateMinVersion(new File(ca.template.directory, "template.json"))

    println(s"Engine template ${ca.template.repository} is now ready at " +
      ca.template.directory)

    0
  }

  def verifyTemplateMinVersion(templateJsonFile: File): Unit = {
    val metadata = templateMetaData(templateJsonFile)

    metadata.pioVersionMin.foreach { pvm =>
      if (Version(BuildInfo.version) < Version(pvm)) {
        error(s"This engine template requires at least PredictionIO $pvm. " +
          s"The template may not work with PredictionIO ${BuildInfo.version}.")
        sys.exit(1)
      }
    }
  }

}
