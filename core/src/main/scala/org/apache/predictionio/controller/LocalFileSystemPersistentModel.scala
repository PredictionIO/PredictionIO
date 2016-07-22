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

import org.apache.spark.SparkContext

/** This trait is a convenience helper for persisting your model to the local
  * filesystem. This trait and [[LocalFileSystemPersistentModelLoader]] contain
  * concrete implementation and need not be implemented.
  *
  * The underlying implementation is [[Utils.save]].
  *
  * {{{
  * class MyModel extends LocalFileSystemPersistentModel[MyParams] {
  *   ...
  * }
  *
  * object MyModel extends LocalFileSystemPersistentModelLoader[MyParams, MyModel] {
  *   ...
  * }
  * }}}
  *
  * @tparam AP Algorithm parameters class.
  * @see [[LocalFileSystemPersistentModelLoader]]
  * @group Algorithm
  */
trait LocalFileSystemPersistentModel[AP <: Params] extends PersistentModel[AP] {
  def save(id: String, params: AP, sc: SparkContext): Boolean = {
    Utils.save(id, this)
    true
  }
}

/** Implement an object that extends this trait for PredictionIO to support
  * loading a persisted model from local filesystem during serving deployment.
  *
  * The underlying implementation is [[Utils.load]].
  *
  * @tparam AP Algorithm parameters class.
  * @tparam M Model class.
  * @see [[LocalFileSystemPersistentModel]]
  * @group Algorithm
  */
trait LocalFileSystemPersistentModelLoader[AP <: Params, M]
  extends PersistentModelLoader[AP, M] {
  def apply(id: String, params: AP, sc: Option[SparkContext]): M = {
    Utils.load(id).asInstanceOf[M]
  }
}

/** DEPRECATED. Use [[LocalFileSystemPersistentModel]] instead.
  *
  * @group Algorithm */
@deprecated("Use LocalFileSystemPersistentModel instead.", "0.9.2")
trait IFSPersistentModel[AP <: Params] extends LocalFileSystemPersistentModel[AP]

/** DEPRECATED. Use [[LocalFileSystemPersistentModelLoader]] instead.
  *
  * @group Algorithm */
@deprecated("Use LocalFileSystemPersistentModelLoader instead.", "0.9.2")
trait IFSPersistentModelLoader[AP <: Params, M] extends LocalFileSystemPersistentModelLoader[AP, M]
