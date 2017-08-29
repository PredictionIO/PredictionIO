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


package org.apache.predictionio.workflow

/** :: DeveloperApi ::
  * Singleton object that collects anonymous functions to be
  * executed to allow the process to end gracefully.
  *
  * For example, the Elasticsearch REST storage client
  * maintains an internal connection pool that must
  * be closed to allow the process to exit.
  */
object CleanupFunctions {
  @volatile private var functions: Seq[() => Unit] = Seq.empty[() => Unit]

  /** Add a function to be called during cleanup.
    *
    * {{{
    * import org.apache.predictionio.workflow.CleanupFunctions
    *
    * CleanupFunctions.add { MyStorageClass.close }
    * }}}
    *
    * @param anonymous function containing cleanup code.
    */
  def add(f: () => Unit): Seq[() => Unit] = {
    functions = functions :+ f
    functions
  }

  /** Call all cleanup functions in order added.
    *
    * {{{
    * import org.apache.predictionio.workflow.CleanupFunctions
    *
    * try {
    *   // Much code that needs cleanup
    *   // whether successful or error thrown.
    * } finally {
    *   CleanupFunctions.run()
    * }
    * }}}
    *
    * @param anonymous function containing cleanup code.
    */
  def run(): Unit = {
    functions.foreach { f => f() }
  }
}
