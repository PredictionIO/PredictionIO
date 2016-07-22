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

package org.apache.predictionio.tools.console

import org.apache.predictionio.tools.Runner

case class ImportArgs(
  appId: Int = 0,
  channel: Option[String] = None,
  inputPath: String = "")

object Import {
  def fileToEvents(ca: ConsoleArgs): Int = {
    val channelArg = ca.imprt.channel
      .map(ch => Seq("--channel", ch)).getOrElse(Nil)
    Runner.runOnSpark(
      "org.apache.predictionio.tools.imprt.FileToEvents",
      Seq(
        "--appid",
        ca.imprt.appId.toString,
        "--input",
        ca.imprt.inputPath) ++ channelArg,
      ca,
      Nil)
  }
}
