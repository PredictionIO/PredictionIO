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

package org.apache.predictionio.tools

import org.apache.predictionio.core.BuildInfo
import org.apache.predictionio.tools.ReturnTypes._

import grizzled.slf4j.Logging
import java.io.File


object ReturnTypes {
  sealed case class Ok()

  type MaybeError = Either[String, Ok]
  type Expected[T] = Either[String, T]

  val Success: MaybeError = Right(Ok())
}

trait EitherLogging extends Logging {
  import ReturnTypes._

  protected def logAndFail[T](msg: => String): Expected[T] = {
    error(msg)
    Left(msg)
  }

  protected def logOnFail[T](msg: => String, t: => Throwable): Expected[T] = {
    error(msg, t)
    Left(msg)
  }

  protected def logAndReturn[T](value: T, msg: => Any): Expected[T] = {
    info(msg)
    Right(value)
  }

  protected def logAndSucceed(msg: => Any): MaybeError = {
    info(msg)
    Success
  }
}

object Common extends EitherLogging {

  def getSparkHome(sparkHome: Option[String]): String = {
    sparkHome getOrElse {
      sys.env.getOrElse("SPARK_HOME", ".")
    }
  }

  def getCoreDir(pioHome: String): String = {
    if (new File(pioHome + File.separator + "RELEASE").exists) {
      pioHome + File.separator + "lib"
    } else {
      Array(pioHome, "assembly", "src", "universal", "lib")
        .mkString(File.separator)
    }
  }

  def getEngineDirPath(directory: Option[String]): String = {
    new File(directory.getOrElse(".")).getCanonicalPath
  }

  def jarFilesForScala(directory: String): Array[File] = {
    def recursiveListFiles(f: File): Array[File] = {
      Option(f.listFiles) map { these =>
        these ++ these.filter(_.isDirectory).flatMap(recursiveListFiles)
      } getOrElse Array[File]()
    }
    def jarFilesForScalaFilter(jars: Array[File]): Array[File] =
      jars.filterNot { f =>
        f.getName.toLowerCase.endsWith("-javadoc.jar") ||
        f.getName.toLowerCase.endsWith("-sources.jar")
      }
    def jarFilesAt(path: File): Array[File] = recursiveListFiles(path) filter {
      _.getName.toLowerCase.endsWith(".jar")
    }

    val engineDir = getEngineDirPath(Some(directory))
    val libFiles = jarFilesForScalaFilter(
      jarFilesAt(new File(engineDir, "lib")))
    val targetFiles = jarFilesForScalaFilter(jarFilesAt(new File(engineDir,
      "target" + File.separator + s"scala-${BuildInfo.scalaBinaryVersion}")))
    // Use libFiles is target is empty.
    if (targetFiles.size > 0) targetFiles else libFiles
  }

  def jarFilesForSpark(pioHome: String): Array[File] = {
    def jarFilesAt(path: File): Array[File] = path.listFiles filter {
      _.getName.toLowerCase.endsWith(".jar")
    }
    jarFilesAt(new File(getCoreDir(pioHome) + File.separator + "spark"))
  }

  def coreAssembly(pioHome: String): Expected[File] = {
    val core = s"pio-assembly-${BuildInfo.version}.jar"
    val coreFile = new File(getCoreDir(pioHome), core)
    if (coreFile.exists) {
      Right(coreFile)
    } else {
      logAndFail(s"PredictionIO Core Assembly (${coreFile.getCanonicalPath}) does " +
        "not exist. Aborting.")
    }
  }
}
