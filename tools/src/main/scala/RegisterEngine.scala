/** Copyright 2014 TappingStone, Inc.
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

package io.prediction.tools

import io.prediction.data.storage.Storage
import io.prediction.data.storage.EngineManifest
import io.prediction.data.storage.EngineManifestSerializer

import grizzled.slf4j.Logging
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.json4s._
import org.json4s.native.Serialization.read

import scala.io.Source

import java.io.File

object RegisterEngine extends Logging {

  val engineManifests = Storage.getMetaDataEngineManifests
  implicit val formats = DefaultFormats + new EngineManifestSerializer

  def builtinEngine(jsonManifest: File): Boolean = {
    val jsonString = try {
      Source.fromFile(jsonManifest).mkString
    } catch {
      case e: java.io.FileNotFoundException =>
        error(s"Engine manifest file not found: ${e.getMessage}. Aborting.")
        sys.exit(1)
    }
    val engineManifest = read[EngineManifest](jsonString)
    engineManifest.id.startsWith("io.prediction.engines.")
  }

  def registerEngine(
      jsonManifest: File,
      engineFiles: Seq[File],
      copyLocal: Boolean = false): Unit = {
    val jsonString = try {
      Source.fromFile(jsonManifest).mkString
    } catch {
      case e: java.io.FileNotFoundException =>
        error(s"Engine manifest file not found: ${e.getMessage}. Aborting.")
        sys.exit(1)
    }
    val engineManifest = read[EngineManifest](jsonString)

    // Configure local FS or HDFS
    val conf = new Configuration
    val fss =
      if (copyLocal)
        Seq(FileSystem.get(conf), FileSystem.getLocal(conf))
      else
        Seq(FileSystem.get(conf))
    val enginesdir = sys.env.get("PIO_FS_ENGINESDIR") match {
      case Some(s) => s
      case None =>
        error("Environmental variable PIO_FS_ENGINESDIR is not set. Is " +
          "conf/pio-env.sh present?")
        sys.exit(1)
    }

    val destDir = Seq(enginesdir, engineManifest.id, engineManifest.version)
    val destPath = new Path(destDir.mkString(Path.SEPARATOR_CHAR + ""))
    fss foreach { fs =>
      fs.mkdirs(destPath)
    }
    val files = fss flatMap { fs =>
      engineFiles map { f =>
        val destFilePath =
          new Path(destDir.:+(f.getName).mkString(Path.SEPARATOR_CHAR + ""))
        val destPathString = fs.makeQualified(destFilePath).toString
        //if (fs.exists(destFilePath) &&
        //  f.length == fs.getFileStatus(destFilePath).getLen)
        //  info(s"Skip copying ${f.toURI} because ${destPathString} exists " +
        //    "and their file sizes are equal")
        //else {
        info(s"Copying ${f.toURI} to ${destPathString}")
        fs.copyFromLocalFile(new Path(f.toURI), destPath)
        //}
        destPathString
      }
    }
    val uniqueFiles = files.groupBy(identity).map(_._2.head).toSeq

    info(s"Registering engine ${engineManifest.id} ${engineManifest.version}")
    engineManifests.update(engineManifest.copy(files = uniqueFiles), true)
  }

  def unregisterEngine(jsonManifest: File): Unit = {
    val jsonString = try {
      Source.fromFile(jsonManifest).mkString
    } catch {
      case e: java.io.FileNotFoundException =>
        error(s"Engine manifest file not found: ${e.getMessage}. Aborting.")
        sys.exit(1)
    }
    val fileEngineManifest = read[EngineManifest](jsonString)
    val engineManifest = engineManifests.get(
      fileEngineManifest.id,
      fileEngineManifest.version)

    engineManifest map { em =>
      val conf = new Configuration
      val fs = FileSystem.get(conf)

      em.files foreach { f =>
        val path = new Path(f)
        info(s"Removing ${f}")
        fs.delete(path, false)
      }

      engineManifests.delete(em.id, em.version)
      info(s"Unregistered engine ${em.id} ${em.version}")
    } getOrElse {
      error(s"${fileEngineManifest.id} ${fileEngineManifest.version} is not " +
        "registered.")
    }
  }
}
