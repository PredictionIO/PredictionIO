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

package org.apache.predictionio.tools.commands

import java.io.File

import scala.io.Source

import org.apache.predictionio.core.BuildInfo
import org.apache.predictionio.tools.EitherLogging
import org.apache.predictionio.tools.ReturnTypes._
import org.json4s._
import org.json4s.native.JsonMethods._
import com.github.zafarkhaja.semver.Version

case class TemplateMetaData(
  pioVersionMin: Option[String] = None)

object Template extends EitherLogging {

  def templateMetaData(templateJson: File): TemplateMetaData = {
    if (!templateJson.exists) {
      warn(s"$templateJson does not exist. Template metadata will not be available. " +
        "(This is safe to ignore if you are not working on a template.)")
      TemplateMetaData()
    } else {
      val jsonString = Source.fromFile(templateJson)(scala.io.Codec.ISO8859).mkString
      val json = try {
        parse(jsonString)
      } catch {
        case e: org.json4s.ParserUtil.ParseException =>
          warn(s"$templateJson cannot be parsed. Template metadata will not be available.")
          return TemplateMetaData()
      }
      val pioVersionMin = json \ "pio" \ "version" \ "min"
      pioVersionMin match {
        case JString(s) => TemplateMetaData(pioVersionMin = Some(s))
        case _ => TemplateMetaData()
      }
    }
  }

  def verifyTemplateMinVersion(templateJsonFile: File): MaybeError = {
    val metadata = templateMetaData(templateJsonFile)

    for (pvm <- metadata.pioVersionMin) {
      if(Version.valueOf(BuildInfo.version).lessThan(Version.valueOf(pvm))){
        return logAndFail(s"This engine template requires at least PredictionIO $pvm. " +
          s"The template may not work with PredictionIO ${BuildInfo.version}.")
      }
    }
    Success
  }
}
