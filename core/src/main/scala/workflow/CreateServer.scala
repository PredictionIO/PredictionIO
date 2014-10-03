/** Copyright 2014 TappingStone, Inc.
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

package io.prediction.workflow

import io.prediction.controller.IEngineFactory
import io.prediction.controller.EmptyParams
import io.prediction.controller.Engine
import io.prediction.controller.PAlgorithm
import io.prediction.controller.Params
import io.prediction.controller.java.LJavaAlgorithm
import io.prediction.controller.java.LJavaServing
import io.prediction.core.BaseAlgorithm
import io.prediction.core.BaseServing
import io.prediction.core.Doer
import io.prediction.data.storage.EngineInstance
import io.prediction.data.storage.EngineManifest
import io.prediction.data.storage.Storage

import akka.actor.{ Actor, ActorRef, ActorSystem, Kill, Props }
import akka.event.Logging
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.github.nscala_time.time.Imports.DateTime
import com.google.gson.Gson
import com.twitter.chill.KryoInjection
import com.twitter.chill.ScalaKryoInstantiator
import grizzled.slf4j.Logging
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.{ read, write }
import spray.can.Http
import spray.routing._
import spray.http._
import spray.http.MediaTypes._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.existentials
import scala.reflect.runtime.universe

import java.io.File
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.net.URLClassLoader

class KryoInstantiator(classLoader: ClassLoader) extends ScalaKryoInstantiator {
  override def newKryo = {
    val kryo = super.newKryo
    kryo.setClassLoader(classLoader)
    kryo
  }
}

case class ServerConfig(
  engineInstanceId: String = "",
  engineId: Option[String] = None,
  engineVersion: Option[String] = None,
  ip: String = "localhost",
  port: Int = 8000)

case class StartServer()
case class StopServer()
case class ReloadServer()

object CreateServer extends Logging {
  val actorSystem = ActorSystem("pio-server")
  val engineInstances = Storage.getMetaDataEngineInstances
  val engineManifests = Storage.getMetaDataEngineManifests
  val modeldata = Storage.getModelDataModels

  def main(args: Array[String]): Unit = {
    val parser = new scopt.OptionParser[ServerConfig]("CreateServer") {
      opt[String]("engineId") action { (x, c) =>
        c.copy(engineId = Some(x))
      } text("Engine ID.")
      opt[String]("engineVersion") action { (x, c) =>
        c.copy(engineVersion = Some(x))
      } text("Engine version.")
      opt[String]("ip") action { (x, c) =>
        c.copy(ip = x)
      } text("IP to bind to (default: localhost).")
      opt[Int]("port") action { (x, c) =>
        c.copy(port = x)
      } text("Port to bind to (default: 8000).")
      opt[String]("engineInstanceId") required() action { (x, c) =>
        c.copy(engineInstanceId = x)
      } text("Engine instance ID.")
    }

    parser.parse(args, ServerConfig()) map { sc =>
      engineInstances.get(sc.engineInstanceId) map { engineInstance =>
        val engineId = sc.engineId.getOrElse(engineInstance.engineId)
        val engineVersion = sc.engineVersion.getOrElse(
          engineInstance.engineVersion)
        engineManifests.get(engineId, engineVersion) map { manifest =>
          WorkflowUtils.checkUpgrade("deployment")
          val engineFactoryName = manifest.engineFactory
          val master = actorSystem.actorOf(Props(
            classOf[MasterActor],
            sc,
            engineInstance,
            engineFactoryName,
            manifest),
          "master")
          implicit val timeout = Timeout(5.seconds)
          master ? StartServer()
          actorSystem.awaitTermination
        } getOrElse {
          error(s"Invalid engine ID or version. Aborting server.")
        }
      } getOrElse {
        error(s"Invalid engine instance ID. Aborting server.")
      }
    }
  }

  def createServerActorWithEngine[TD, DP, PD, Q, P, A](
    sc: ServerConfig,
    engineInstance: EngineInstance,
    engine: Engine[TD, DP, PD, Q, P, A],
    engineLanguage: EngineLanguage.Value,
    manifest: EngineManifest): ActorRef = {
    implicit val formats = DefaultFormats

    val algorithmsParamsWithNames =
      read[Seq[(String, JValue)]](engineInstance.algorithmsParams).map {
        case (algoName, params) =>
          val extractedParams = WorkflowUtils.extractParams(
            engineLanguage,
            compact(render(params)),
            engine.algorithmClassMap(algoName))
          (algoName, extractedParams)
      }

    val algorithmsParams = algorithmsParamsWithNames.map { _._2 }

    val algorithms = algorithmsParamsWithNames.map { case (n, p) =>
      Doer(engine.algorithmClassMap(n), p)
    }

    val servingParams =
      if (engineInstance.servingParams == "")
        EmptyParams()
      else
        WorkflowUtils.extractParams(
          engineLanguage,
          engineInstance.servingParams,
          engine.servingClass)
    val serving = Doer(engine.servingClass, servingParams)

    val pAlgorithmExists =
      algorithms.exists(_.isInstanceOf[PAlgorithm[_, PD, _, Q, P]])
    val sparkContext =
      if (pAlgorithmExists)
        Some(WorkflowContext(engineInstance.batch, engineInstance.env))
      else
        None
    val evalPreparedMap = sparkContext map { sc =>
      logger.info("Data Source")
      val dataSourceParams = WorkflowUtils.extractParams(
        engineLanguage,
        engineInstance.dataSourceParams,
        engine.dataSourceClass)
      val dataSource = Doer(engine.dataSourceClass, dataSourceParams)
      val evalParamsDataMap
      : Map[EI, (DP, TD, RDD[(Q, A)])] = dataSource
        .readBase(sc)
        .zipWithIndex
        .map(_.swap)
        .toMap
      val evalDataMap: Map[EI, (TD, RDD[(Q, A)])] = evalParamsDataMap.map {
        case(ei, e) => (ei -> (e._2, e._3))
      }
      logger.info("Preparator")
      val preparatorParams = WorkflowUtils.extractParams(
        engineLanguage,
        engineInstance.preparatorParams,
        engine.preparatorClass)
      val preparator = Doer(engine.preparatorClass, preparatorParams)

      val evalPreparedMap: Map[EI, PD] = evalDataMap
      .map{ case (ei, data) => (ei, preparator.prepareBase(sc, data._1)) }
      logger.info("Preparator complete")

      evalPreparedMap
    }

    val kryoInstantiator = new KryoInstantiator(getClass.getClassLoader)
    val kryo = KryoInjection.instance(kryoInstantiator)
    val modelsFromEngineInstance =
      kryo.invert(modeldata.get(engineInstance.id).get.models).get.
        asInstanceOf[Seq[Seq[Any]]]
    val models = modelsFromEngineInstance.head.zip(algorithms).
      zip(algorithmsParams).map {
        case ((m, a), p) =>
          if (m.isInstanceOf[PersistentModelManifest]) {
            info("Custom-persisted model detected for algorithm " +
              a.getClass.getName)
            WorkflowUtils.getPersistentModel(
              m.asInstanceOf[PersistentModelManifest],
              engineInstance.id,
              p,
              sparkContext,
              getClass.getClassLoader)
          } else if (a.isInstanceOf[PAlgorithm[_, _, _, Q, P]]) {
            info(s"Parallel model detected for algorithm ${a.getClass.getName}")
            a.trainBase(sparkContext.get, evalPreparedMap.get(0))
          } else {
            try {
              info(s"Loaded model ${m.getClass.getName} for algorithm " +
                s"${a.getClass.getName}")
              m
            } catch {
              case e: NullPointerException =>
                warn(s"Null model detected for algorithm ${a.getClass.getName}")
                m
            }
          }
      }

    actorSystem.actorOf(
      Props(
        classOf[ServerActor[Q, P]],
        sc,
        engineInstance,
        engine,
        engineLanguage,
        manifest,
        algorithms,
        algorithmsParams,
        models,
        serving,
        servingParams))
  }
}

class MasterActor(
    sc: ServerConfig,
    engineInstance: EngineInstance,
    engineFactoryName: String,
    manifest: EngineManifest) extends Actor {
  val log = Logging(context.system, this)
  implicit val system = context.system
  var sprayHttpListener: Option[ActorRef] = None
  var currentServerActor: Option[ActorRef] = None
  def receive = {
    case x: StartServer =>
      val actor = createServerActor(
        sc,
        engineInstance,
        engineFactoryName,
        manifest)
      IO(Http) ! Http.Bind(actor, interface = sc.ip, port = sc.port)
      currentServerActor = Some(actor)
    case x: StopServer =>
      log.info(s"Stop server command received.")
      sprayHttpListener.map { l =>
        log.info("Server is shutting down.")
        l ! Http.Unbind(5.seconds)
        system.shutdown
      } getOrElse {
        log.warning("No active server is running.")
      }
    case x: ReloadServer =>
      log.info("Reload server command received.")
      val latestEngineInstance =
        CreateServer.engineInstances.getLatestCompleted(
          manifest.id,
          manifest.version)
      latestEngineInstance map { lr =>
        val actor = createServerActor(sc, lr, engineFactoryName, manifest)
        sprayHttpListener.map { l =>
          l ! Http.Unbind(5.seconds)
          IO(Http) ! Http.Bind(actor, interface = sc.ip, port = sc.port)
          currentServerActor.get ! Kill
          currentServerActor = Some(actor)
        } getOrElse {
          log.warning("No active server is running. Abort reloading.")
        }
      } getOrElse {
        log.warning(
          s"No latest completed engine instance for ${manifest.id} " +
          s"${manifest.version}. Abort reloading.")
      }
    case x: Http.Bound =>
      log.info("Bind successful. Ready to serve.")
      sprayHttpListener = Some(sender)
    case x: Http.CommandFailed =>
      log.error("Bind failed. Shutting down.")
      system.shutdown
  }

  def createServerActor(
      sc: ServerConfig,
      engineInstance: EngineInstance,
      engineFactoryName: String,
      manifest: EngineManifest): ActorRef = {
    val (engineLanguage, engine) =
      WorkflowUtils.getEngine(engineFactoryName, getClass.getClassLoader)
    CreateServer.createServerActorWithEngine(
      sc,
      engineInstance,
      engine,
      engineLanguage,
      manifest)
  }
}

class ServerActor[Q, P](
    val args: ServerConfig,
    val engineInstance: EngineInstance,
    val engine: Engine[_, _, _, Q, P, _],
    val engineLanguage: EngineLanguage.Value,
    val manifest: EngineManifest,
    val algorithms: Seq[BaseAlgorithm[_ <: Params, _, _, Q, P]],
    val algorithmsParams: Seq[Params],
    val models: Seq[Any],
    val serving: BaseServing[_ <: Params, Q, P],
    val servingParams: Params) extends Actor with HttpService {
  val serverStartTime = DateTime.now
  lazy val gson = new Gson
  val log = Logging(context.system, this)
  val (javaAlgorithms, scalaAlgorithms) =
    algorithms.partition(_.isInstanceOf[LJavaAlgorithm[_, _, _, Q, P]])

  def actorRefFactory = context

  def receive = runRoute(myRoute)

  val myRoute =
    path("") {
      get {
        respondWithMediaType(`text/html`) {
          detach() {
            complete {
              html.index(
                args,
                manifest,
                engineInstance,
                algorithms.map(_.toString),
                algorithmsParams.map(_.toString),
                models.map(_.toString),
                servingParams.toString,
                serverStartTime).toString
            }
          }
        }
      }
    } ~
    path("queries.json") {
      post {
        detach() {
          entity(as[String]) { queryString =>
            try {
              val javaQuery = if (!javaAlgorithms.isEmpty) {
                Some(gson.fromJson(
                  queryString,
                  javaAlgorithms.head.asInstanceOf[LJavaAlgorithm[_, _, _, Q, P]].
                    queryClass))
              } else None
              val scalaQuery = if (!scalaAlgorithms.isEmpty) {
                Some(Extraction.extract(parse(queryString))(
                  scalaAlgorithms.head.querySerializer,
                  scalaAlgorithms.head.queryManifest))
              } else None
              val predictions = algorithms.zipWithIndex.map { case (a, ai) =>
                if (a.isInstanceOf[LJavaAlgorithm[_, _, _, Q, P]])
                  a.predictBase(models(ai), javaQuery.get)
                else
                  a.predictBase(models(ai), scalaQuery.get)
              }
              val json = if (serving.isInstanceOf[LJavaServing[_, Q, P]]) {
                val prediction = serving.serveBase(javaQuery.get, predictions)
                gson.toJson(prediction)
              } else {
                val prediction = serving.serveBase(scalaQuery.get, predictions)
                compact(render(Extraction.decompose(prediction)(
                  scalaAlgorithms.head.querySerializer)))
              }
              respondWithMediaType(`application/json`) {
                complete(json)
              }
            } catch {
              case e: MappingException =>
                log.error(
                  s"Query '${queryString}' is invalid. Reason: ${e.getMessage}")
                complete(StatusCodes.BadRequest, e.getMessage)
            }
          }
        }
      }
    } ~
    path("reload") {
      get {
        complete {
          context.actorSelection("/user/master") ! ReloadServer()
          "Reloading..."
        }
      }
    } ~
    path("stop") {
      get {
        complete {
          context.system.scheduler.scheduleOnce(1.seconds) {
            context.actorSelection("/user/master") ! StopServer()
          }
          "Shutting down..."
        }
      }
    } ~
    pathPrefix("assets") {
      getFromResourceDirectory("assets")
    }
}
