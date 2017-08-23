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

import org.apache.predictionio.tools.Common._
import org.apache.predictionio.tools.ReturnTypes._
import org.apache.predictionio.workflow.JsonExtractorOption
import org.apache.predictionio.workflow.JsonExtractorOption.JsonExtractorOption

import java.io.File
import grizzled.slf4j.Logging

import scala.sys.process._

case class BatchPredictArgs(
  inputFilePath: String = "batchpredict-input.json",
  outputFilePath: String = "batchpredict-output.json",
  queryPartitions: Option[Int] = None,
  variantJson: Option[File] = None,
  jsonExtractor: JsonExtractorOption = JsonExtractorOption.Both)


object RunBatchPredict extends Logging {

  def runBatchPredict(
    engineInstanceId: String,
    batchPredictArgs: BatchPredictArgs,
    sparkArgs: SparkArgs,
    pioHome: String,
    engineDirPath: String,
    verbose: Boolean = false): Expected[(Process, () => Unit)] = {

    val jarFiles = jarFilesForScala(engineDirPath).map(_.toURI) ++
      Option(new File(pioHome, "plugins").listFiles())
        .getOrElse(Array.empty[File]).map(_.toURI)
    val args = Seq[String](
      "--input",
      batchPredictArgs.inputFilePath,
      "--output",
      batchPredictArgs.outputFilePath,
      "--engineInstanceId",
      engineInstanceId,
      "--engine-variant",
      batchPredictArgs.variantJson.getOrElse(
        new File(engineDirPath, "engine.json")).getCanonicalPath) ++
      (if (batchPredictArgs.queryPartitions.isEmpty) Nil
        else Seq("--query-partitions",
                  batchPredictArgs.queryPartitions.get.toString)) ++
      (if (verbose) Seq("--verbose") else Nil) ++
      Seq("--json-extractor", batchPredictArgs.jsonExtractor.toString)

    Runner.runOnSpark(
      "org.apache.predictionio.workflow.BatchPredict",
      args, sparkArgs, jarFiles, pioHome, verbose)
  }
}
