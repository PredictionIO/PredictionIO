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

package org.apache.predictionio.data.storage

import com.google.common.io.BaseEncoding
import org.apache.predictionio.annotation.DeveloperApi
import org.json4s._

/** :: DeveloperApi ::
  * Stores model for each engine instance
  *
  * @param id ID of the model, which should be the same as engine instance ID
  * @param models Trained models of all algorithms
  * @group Model Data
  */
@DeveloperApi
case class Model(
  id: String,
  models: Array[Byte])

/** :: DeveloperApi ::
  * Base trait for of the [[Model]] data access object
  *
  * @group Model Data
  */
@DeveloperApi
trait Models {
  /** Insert a new [[Model]] */
  def insert(i: Model): Unit

  /** Get a [[Model]] by ID */
  def get(id: String): Option[Model]

  /** Delete a [[Model]] */
  def delete(id: String): Unit
}

/** :: DeveloperApi ::
  * JSON4S serializer for [[Model]]
  *
  * @group Model Data
  */
@DeveloperApi
class ModelSerializer extends CustomSerializer[Model](
  format => ({
    case JObject(fields) =>
      implicit val formats = DefaultFormats
      val seed = Model(
          id = "",
          models = Array[Byte]())
      fields.foldLeft(seed) { case (i, field) =>
        field match {
          case JField("id", JString(id)) => i.copy(id = id)
          case JField("models", JString(models)) =>
            i.copy(models = BaseEncoding.base64.decode(models))
          case _ => i
        }
      }
  },
  {
    case i: Model =>
      JObject(
        JField("id", JString(i.id)) ::
        JField("models", JString(BaseEncoding.base64.encode(i.models))) ::
        Nil)
  }
))
