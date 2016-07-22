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

package org.apache.predictionio.tools

import java.io.File

import grizzled.slf4j.Logging
import org.apache.predictionio.data.storage.EngineManifest
import org.apache.predictionio.data.storage.EngineManifestSerializer
import org.apache.predictionio.data.storage.Storage
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.json4s._
import org.json4s.native.Serialization.read

import scala.io.Source

object RegisterEngine extends Logging {
  val engineManifests = Storage.getMetaDataEngineManifests
  implicit val formats = DefaultFormats + new EngineManifestSerializer

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

    info(s"Registering engine ${engineManifest.id} ${engineManifest.version}")
    engineManifests.update(
      engineManifest.copy(files = engineFiles.map(_.toURI.toString)), true)
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
