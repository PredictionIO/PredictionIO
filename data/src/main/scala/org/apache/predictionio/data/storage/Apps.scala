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

import org.apache.predictionio.annotation.DeveloperApi

/** :: DeveloperApi ::
  * Stores mapping of app IDs and names
  *
  * @param id ID of the app.
  * @param name Name of the app.
  * @param description Long description of the app.
  * @group Meta Data
  */
@DeveloperApi
case class App(
  id: Int,
  name: String,
  description: Option[String])

/** :: DeveloperApi ::
  * Base trait of the [[App]] data access object
  *
  * @group Meta Data
  */
@DeveloperApi
trait Apps {
  /** Insert a new [[App]]. Returns a generated app ID if the supplied app ID is 0. */
  def insert(app: App): Option[Int]

  /** Get an [[App]] by app ID */
  def get(id: Int): Option[App]

  /** Get an [[App]] by app name */
  def getByName(name: String): Option[App]

  /** Get all [[App]]s */
  def getAll(): Seq[App]

  /** Update an [[App]] */
  def update(app: App): Unit

  /** Delete an [[App]] */
  def delete(id: Int): Unit
}
