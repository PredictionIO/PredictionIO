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

package org.apache.predictionio.tools

import org.apache.predictionio.tools.console.Console
import org.apache.predictionio.tools.Common._
import org.apache.predictionio.tools.ReturnTypes._
import org.apache.predictionio.workflow.JsonExtractorOption
import org.apache.predictionio.workflow.JsonExtractorOption.JsonExtractorOption

import java.io.File
import grizzled.slf4j.Logging

import scala.sys.process._

case class WorkflowArgs(
  batch: String = "",
  variantJson: Option[File] = None,
  verbosity: Int = 0,
  engineParamsKey: Option[String] = None,
  engineFactory: Option[String] = None,
  evaluation: Option[String] = None,
  engineParamsGenerator: Option[String] = None,
  stopAfterRead: Boolean = false,
  stopAfterPrepare: Boolean = false,
  skipSanityCheck: Boolean = false,
  mainPyFile: Option[String] = None,
  jsonExtractor: JsonExtractorOption = JsonExtractorOption.Both)

object RunWorkflow extends Logging {
  def runWorkflow(
    wa: WorkflowArgs,
    sa: SparkArgs,
    pioHome: String,
    engineDirPath: String,
    verbose: Boolean = false): Expected[(Process, () => Unit)] = {

    val jarFiles = jarFilesForScala(engineDirPath).map(_.toURI)
    val args =
      (if (wa.mainPyFile.isEmpty) {
        val variantJson = wa.variantJson.getOrElse(new File(engineDirPath, "engine.json"))
        val ei = Console.getEngineInfo(variantJson, engineDirPath)
        Seq(
          "--engine-id", ei.engineId,
          "--engine-version", ei.engineVersion,
          "--engine-variant", variantJson.toURI.toString)
      } else Nil) ++
      wa.engineFactory.map(
        x => Seq("--engine-factory", x)).getOrElse(Nil) ++
      wa.engineParamsKey.map(
        x => Seq("--engine-params-key", x)).getOrElse(Nil) ++
      (if (wa.batch != "") Seq("--batch", wa.batch) else Nil) ++
      (if (verbose) Seq("--verbose") else Nil) ++
      (if (wa.skipSanityCheck) Seq("--skip-sanity-check") else Nil) ++
      (if (wa.stopAfterRead) Seq("--stop-after-read") else Nil) ++
      (if (wa.stopAfterPrepare) Seq("--stop-after-prepare") else Nil) ++
      wa.evaluation.map(x => Seq("--evaluation-class", x)).
        getOrElse(Nil) ++
      // If engineParamsGenerator is specified, it overrides the evaluation.
      wa.engineParamsGenerator.orElse(wa.evaluation)
        .map(x => Seq("--engine-params-generator-class", x))
        .getOrElse(Nil) ++
      Seq("--json-extractor", wa.jsonExtractor.toString,
          "--verbosity", wa.verbosity.toString)

    val resourceName = wa.mainPyFile match {
      case Some(x) => x
      case _ => "org.apache.predictionio.workflow.CreateWorkflow"
    }
    Runner.runOnSpark(
      resourceName,
      args,
      sa,
      jarFiles,
      pioHome,
      verbose)
  }
}
