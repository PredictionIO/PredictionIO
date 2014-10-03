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

package io.prediction.controller.java

import scala.collection.JavaConversions._
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

/** Internal Java utilities. */
object JavaUtils {
  // This "fake" tags are adopted from Spark's Java API.
  // Scala requires manifest or classtag for some data. But Prediction.IO
  // doesn't really need it as our system is oblivious to the actual data. We
  // pass a fake ClassTag / Manifest to keep the scala compiler happy.
  def fakeClassTag[T]: ClassTag[T] = {
    ClassTag.AnyRef.asInstanceOf[ClassTag[T]]
  }

  def fakeManifest[T]: Manifest[T] = {
    manifest[AnyRef].asInstanceOf[Manifest[T]]
  }
}
