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

import java.security.SecureRandom

import org.apache.predictionio.annotation.DeveloperApi
import org.apache.commons.codec.binary.Base64

/** :: DeveloperApi ::
  * Stores mapping of access keys, app IDs, and lists of allowed event names
  *
  * @param key Access key
  * @param appid App ID
  * @param events List of allowed events for this particular app key
  * @group Meta Data
  */
@DeveloperApi
case class AccessKey(
  key: String,
  appid: Int,
  events: Seq[String])

/** :: DeveloperApi ::
  * Base trait of the [[AccessKey]] data access object
  *
  * @group Meta Data
  */
@DeveloperApi
trait AccessKeys {
  /** Insert a new [[AccessKey]]. If the key field is empty, a key will be
    * generated.
    */
  def insert(k: AccessKey): Option[String]

  /** Get an [[AccessKey]] by key */
  def get(k: String): Option[AccessKey]

  /** Get all [[AccessKey]]s */
  def getAll(): Seq[AccessKey]

  /** Get all [[AccessKey]]s for a particular app ID */
  def getByAppid(appid: Int): Seq[AccessKey]

  /** Update an [[AccessKey]] */
  def update(k: AccessKey): Unit

  /** Delete an [[AccessKey]] */
  def delete(k: String): Unit

  /** Default implementation of key generation */
  def generateKey: String = {
    val sr = SecureRandom.getInstanceStrong
    val srBytes = Array.fill(48)(0.toByte)
    sr.nextBytes(srBytes)
    Base64.encodeBase64URLSafeString(srBytes)
  }
}
