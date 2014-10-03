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

package io.prediction.core

import io.prediction.controller.Params

import grizzled.slf4j.Logging
import org.json4s._
import org.json4s.ext.JodaTimeSerializers
import org.json4s.native.JsonMethods._

import scala.reflect._

abstract class AbstractDoer[P <: Params : ClassTag]
extends Serializable {
  override def toString(): String = {
    val t = classTag[P].runtimeClass.getName
    s"${this.getClass.getName}(${t})"
  }
}


object Doer extends Logging {
  def apply[C <: AbstractDoer[_ <: Params]] (
    cls: Class[_ <: C], params: Params): C = {

    // Subclasses only allows two kind of constructors.
    // 1. Constructor with P <: Params.
    // 2. Emtpy constructor.
    // First try (1), if failed, try (2).
    try {
      val constr = cls.getConstructor(params.getClass)
      return constr.newInstance(params).asInstanceOf[C]
    } catch {
      case e: NoSuchMethodException => try {
        val zeroConstr = cls.getConstructor()
        return zeroConstr.newInstance().asInstanceOf[C]
      } catch {
        case e: NoSuchMethodException =>
          error(s"${params.getClass.getName} was used as the constructor " +
            s"argument to ${e.getMessage}, but no constructor can handle it. " +
            "Aborting.")
          sys.exit(1)
      }
    }
  }
}
