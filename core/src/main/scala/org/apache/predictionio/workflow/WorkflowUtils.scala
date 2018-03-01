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

import java.io.File
import java.net.URI

import com.google.gson.{Gson, JsonSyntaxException}
import grizzled.slf4j.Logging
import org.apache.log4j.{Level, LogManager}
import org.apache.predictionio.controller._
import org.apache.predictionio.workflow.JsonExtractorOption.JsonExtractorOption
import org.apache.spark.SparkContext
import org.apache.spark.api.java.JavaRDDLike
import org.apache.spark.rdd.RDD
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.language.existentials
import scala.reflect.runtime.universe

/** Collection of reusable workflow related utilities. */
object WorkflowUtils extends Logging {
  @transient private lazy val gson = new Gson

  /** Obtains an Engine object in Scala, or instantiate an Engine in Java.
    *
    * @param engine Engine factory name.
    * @param cl A Java ClassLoader to look for engine-related classes.
    *
    * @throws ClassNotFoundException
    *         Thrown when engine factory class does not exist.
    * @throws NoSuchMethodException
    *         Thrown when engine factory's apply() method is not implemented.
    */
  def getEngine(engine: String, cl: ClassLoader): (EngineLanguage.Value, EngineFactory) = {
    val runtimeMirror = universe.runtimeMirror(cl)
    val engineModule = runtimeMirror.staticModule(engine)
    val engineObject = runtimeMirror.reflectModule(engineModule)
    try {
      (
        EngineLanguage.Scala,
        engineObject.instance.asInstanceOf[EngineFactory]
      )
    } catch {
      case e @ (_: NoSuchFieldException | _: ClassNotFoundException) =>
        (
          EngineLanguage.Java,
          Class.forName(engine).newInstance.asInstanceOf[EngineFactory]
        )
    }
  }

  def getEngineParamsGenerator(epg: String, cl: ClassLoader):
    (EngineLanguage.Value, EngineParamsGenerator) = {
    val runtimeMirror = universe.runtimeMirror(cl)
    val epgModule = runtimeMirror.staticModule(epg)
    val epgObject = runtimeMirror.reflectModule(epgModule)
    try {
      (
        EngineLanguage.Scala,
        epgObject.instance.asInstanceOf[EngineParamsGenerator]
      )
    } catch {
      case e @ (_: NoSuchFieldException | _: ClassNotFoundException) =>
        (
          EngineLanguage.Java,
          Class.forName(epg).newInstance.asInstanceOf[EngineParamsGenerator]
        )
    }
  }

  def getEvaluation(evaluation: String, cl: ClassLoader): (EngineLanguage.Value, Evaluation) = {
    val runtimeMirror = universe.runtimeMirror(cl)
    val evaluationModule = runtimeMirror.staticModule(evaluation)
    val evaluationObject = runtimeMirror.reflectModule(evaluationModule)
    try {
      (
        EngineLanguage.Scala,
        evaluationObject.instance.asInstanceOf[Evaluation]
      )
    } catch {
      case e @ (_: NoSuchFieldException | _: ClassNotFoundException) =>
        (
          EngineLanguage.Java,
          Class.forName(evaluation).newInstance.asInstanceOf[Evaluation]
        )
    }
  }

  /** Converts a JSON document to an instance of Params.
    *
    * @param language Engine's programming language.
    * @param json JSON document.
    * @param clazz Class of the component that is going to receive the resulting
    *              Params instance as a constructor argument.
    * @param jsonExtractor JSON extractor option.
    * @param formats JSON4S serializers for deserialization.
    *
    * @throws MappingException Thrown when JSON4S fails to perform conversion.
    * @throws JsonSyntaxException Thrown when GSON fails to perform conversion.
    */
  def extractParams(
      language: EngineLanguage.Value = EngineLanguage.Scala,
      json: String,
      clazz: Class[_],
      jsonExtractor: JsonExtractorOption,
      formats: Formats = Utils.json4sDefaultFormats): Params = {
    implicit val f = formats
    val pClass = clazz.getConstructors.head.getParameterTypes
    if (pClass.size == 0) {
      if (json != "") {
        warn(s"Non-empty parameters supplied to ${clazz.getName}, but its " +
          "constructor does not accept any arguments. Stubbing with empty " +
          "parameters.")
      }
      EmptyParams()
    } else {
      val apClass = pClass.head
      try {
        JsonExtractor.extract(jsonExtractor, json, apClass, f).asInstanceOf[Params]
      } catch {
        case e@(_: MappingException | _: JsonSyntaxException) =>
          error(
            s"Unable to extract parameters for ${apClass.getName} from " +
              s"JSON string: $json. Aborting workflow.",
            e)
          throw e
      }
    }
  }

  def getParamsFromJsonByFieldAndClass(
      variantJson: JValue,
      field: String,
      classMap: Map[String, Class[_]],
      engineLanguage: EngineLanguage.Value,
      jsonExtractor: JsonExtractorOption): (String, Params) = {
    variantJson findField {
      case JField(f, _) => f == field
      case _ => false
    } map { jv =>
      implicit lazy val formats = Utils.json4sDefaultFormats + new NameParamsSerializer
      val np: NameParams = try {
        jv._2.extract[NameParams]
      } catch {
        case e: Exception =>
          error(s"Unable to extract $field name and params $jv")
          throw e
      }
      val extractedParams = np.params.map { p =>
        try {
          if (!classMap.contains(np.name)) {
            error(s"Unable to find $field class with name '${np.name}'" +
              " defined in Engine.")
            sys.exit(1)
          }
          WorkflowUtils.extractParams(
            engineLanguage,
            compact(render(p)),
            classMap(np.name),
            jsonExtractor,
            formats)
        } catch {
          case e: Exception =>
            error(s"Unable to extract $field params $p")
            throw e
        }
      }.getOrElse(EmptyParams())

      (np.name, extractedParams)
    } getOrElse("", EmptyParams())
  }

  /** Grab environmental variables that starts with 'PIO_'. */
  def pioEnvVars: Map[String, String] =
    sys.env.filter(kv => kv._1.startsWith("PIO_"))

  /** Converts Java (non-Scala) objects to a JSON4S JValue.
    *
    * @param params The Java object to be converted.
    */
  def javaObjectToJValue(params: AnyRef): JValue = parse(gson.toJson(params))

  // Extract debug string by recursively traversing the data.
  def debugString[D](data: D): String = {
    val s: String = data match {
      case rdd: RDD[_] => {
        debugString(rdd.collect())
      }
      case javaRdd: JavaRDDLike[_, _] => {
        debugString(javaRdd.collect())
      }
      case array: Array[_] => {
        "[" + array.map(debugString).mkString(",") + "]"
      }
      case d: AnyRef => {
        d.toString
      }
      case null => "null"
    }
    s
  }

  /** Detect third party software configuration files to be submitted as
    * extras to Apache Spark. This makes sure all executors receive the same
    * configuration.
    */
  def thirdPartyConfFiles: Seq[String] = {
    val thirdPartyFiles = Map(
      "PIO_CONF_DIR" -> "log4j.properties",
      "ES_CONF_DIR" -> "elasticsearch.yml",
      "HADOOP_CONF_DIR" -> "core-site.xml",
      "HBASE_CONF_DIR" -> "hbase-site.xml")

    thirdPartyFiles.keys.toSeq.flatMap { k: String =>
      sys.env.get(k) map { x =>
        val p = Seq(x, thirdPartyFiles(k)).mkString(File.separator)
        if (new File(p).exists) Seq(p) else Seq[String]()
      } getOrElse Seq[String]()
    }
  }

  def thirdPartyClasspaths: Seq[String] = {
    val thirdPartyPaths = Seq(
      "PIO_CONF_DIR",
      "ES_CONF_DIR",
      "POSTGRES_JDBC_DRIVER",
      "MYSQL_JDBC_DRIVER",
      "HADOOP_CONF_DIR",
      "HBASE_CONF_DIR")
    thirdPartyPaths.flatMap(p =>
      sys.env.get(p).map(Seq(_)).getOrElse(Seq[String]())
    )
  }

  def thirdPartyJars: Seq[URI] = {
    val thirdPartyPaths = Seq(
      "POSTGRES_JDBC_DRIVER",
      "MYSQL_JDBC_DRIVER")
    thirdPartyPaths.flatMap(p =>
      sys.env.get(p) map { f =>
        val file = new File(f)
        if (file.exists()) {
          Seq(file.toURI)
        } else {
          warn(s"Environment variable $p is pointing to a nonexistent file $f. Ignoring.")
          Seq.empty
        }
      } getOrElse Seq.empty
    )
  }

  def modifyLogging(verbose: Boolean): Unit = {
    val rootLoggerLevel = if (verbose) Level.TRACE else Level.INFO
    val chattyLoggerLevel = if (verbose) Level.INFO else Level.WARN

    LogManager.getRootLogger.setLevel(rootLoggerLevel)

    LogManager.getLogger("org.elasticsearch").setLevel(chattyLoggerLevel)
    LogManager.getLogger("org.apache.hadoop").setLevel(chattyLoggerLevel)
    LogManager.getLogger("org.apache.spark").setLevel(chattyLoggerLevel)
    LogManager.getLogger("org.eclipse.jetty").setLevel(chattyLoggerLevel)
    LogManager.getLogger("akka").setLevel(chattyLoggerLevel)
  }

  def extractNameParams(jv: JValue): NameParams = {
    implicit val formats = Utils.json4sDefaultFormats
    val nameOpt = (jv \ "name").extract[Option[String]]
    val paramsOpt = (jv \ "params").extract[Option[JValue]]

    if (nameOpt.isEmpty && paramsOpt.isEmpty) {
      error("Unable to find 'name' or 'params' fields in" +
        s" ${compact(render(jv))}.\n" +
        "Since 0.8.4, the 'params' field is required in engine.json" +
        " in order to specify parameters for DataSource, Preparator or" +
        " Serving.\n" +
        "Please go to http://predictionio.apache.org/resources/upgrade/" +
        " for detailed instruction of how to change engine.json.")
      sys.exit(1)
    }

    if (nameOpt.isEmpty) {
      info(s"No 'name' is found. Default empty String will be used.")
    }

    if (paramsOpt.isEmpty) {
      info(s"No 'params' is found. Default EmptyParams will be used.")
    }

    NameParams(
      name = nameOpt.getOrElse(""),
      params = paramsOpt
    )
  }

  def extractSparkConf(root: JValue): List[(String, String)] = {
    def flatten(jv: JValue): List[(List[String], String)] = {
      jv match {
        case JObject(fields) =>
          for ((namePrefix, childJV) <- fields;
               (name, value) <- flatten(childJV))
          yield (namePrefix :: name) -> value
        case JArray(_) => {
          error("Arrays are not allowed in the sparkConf section of engine.js.")
          sys.exit(1)
        }
        case JNothing => Nil
        case _ => List(Nil -> jv.values.toString)
      }
    }

    flatten(root \ "sparkConf").map(x =>
      (x._1.reduce((a, b) => s"$a.$b"), x._2))
  }
}

case class NameParams(name: String, params: Option[JValue])

class NameParamsSerializer extends CustomSerializer[NameParams](format => ( {
  case jv: JValue => WorkflowUtils.extractNameParams(jv)
}, {
  case x: NameParams =>
    JObject(JField("name", JString(x.name)) ::
      JField("params", x.params.getOrElse(JNothing)) :: Nil)
}
  ))

/** Collection of reusable workflow related utilities that touch on Apache
  * Spark. They are separated to avoid compilation problems with certain code.
  */
object SparkWorkflowUtils extends Logging {
  def getPersistentModel[AP <: Params, M](
      pmm: PersistentModelManifest,
      runId: String,
      params: AP,
      sc: Option[SparkContext],
      cl: ClassLoader): M = {
    val runtimeMirror = universe.runtimeMirror(cl)
    val pmmModule = runtimeMirror.staticModule(pmm.className)
    val pmmObject = runtimeMirror.reflectModule(pmmModule)
    try {
      pmmObject.instance.asInstanceOf[PersistentModelLoader[AP, M]](
        runId,
        params,
        sc)
    } catch {
      case e @ (_: NoSuchFieldException | _: ClassNotFoundException) => try {
        val loadMethod = Class.forName(pmm.className).getMethod(
          "load",
          classOf[String],
          classOf[Params],
          classOf[SparkContext])
        loadMethod.invoke(null, runId, params, sc.orNull).asInstanceOf[M]
      } catch {
        case e: ClassNotFoundException =>
          error(s"Model class ${pmm.className} cannot be found.")
          throw e
        case e: NoSuchMethodException =>
          error(
            "The load(String, Params, SparkContext) method cannot be found.")
          throw e
      }
    }
  }
}

class WorkflowInterruption() extends Exception

case class StopAfterReadInterruption() extends WorkflowInterruption

case class StopAfterPrepareInterruption() extends WorkflowInterruption

object EngineLanguage extends Enumeration {
  val Scala, Java = Value
}
