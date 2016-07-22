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

package org.apache.predictionio.controller

import org.apache.predictionio.workflow.KryoInstantiator

import org.json4s._
import org.json4s.ext.JodaTimeSerializers

import scala.io.Source

import _root_.java.io.File
import _root_.java.io.FileOutputStream

/** Controller utilities.
  *
  * @group Helper
  */
object Utils {
  /** Default JSON4S serializers for PredictionIO controllers. */
  val json4sDefaultFormats = DefaultFormats.lossless ++ JodaTimeSerializers.all

  /** Save a model object as a file to a temporary location on local filesystem.
    * It will first try to use the location indicated by the environmental
    * variable PIO_FS_TMPDIR, then fall back to the java.io.tmpdir property.
    *
    * @param id Used as the filename of the file.
    * @param model Model object.
    */
  def save(id: String, model: Any): Unit = {
    val tmpdir = sys.env.getOrElse("PIO_FS_TMPDIR", System.getProperty("java.io.tmpdir"))
    val modelFile = tmpdir + File.separator + id
    (new File(tmpdir)).mkdirs
    val fos = new FileOutputStream(modelFile)
    val kryo = KryoInstantiator.newKryoInjection
    fos.write(kryo(model))
    fos.close
  }

  /** Load a model object from a file in a temporary location on local
    * filesystem. It will first try to use the location indicated by the
    * environmental variable PIO_FS_TMPDIR, then fall back to the java.io.tmpdir
    * property.
    *
    * @param id Used as the filename of the file.
    */
  def load(id: String): Any = {
    val tmpdir = sys.env.getOrElse("PIO_FS_TMPDIR", System.getProperty("java.io.tmpdir"))
    val modelFile = tmpdir + File.separator + id
    val src = Source.fromFile(modelFile)(scala.io.Codec.ISO8859)
    val kryo = KryoInstantiator.newKryoInjection
    val m = kryo.invert(src.map(_.toByte).toArray).get
    src.close
    m
  }
}
