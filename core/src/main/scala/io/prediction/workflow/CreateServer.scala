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

package io.prediction.workflow

import io.prediction.controller.Engine
import io.prediction.controller.Params
import io.prediction.controller.Utils
import io.prediction.controller.WithPrId
import io.prediction.core.BaseAlgorithm
import io.prediction.core.BaseServing
import io.prediction.core.Doer
import io.prediction.data.storage.EngineInstance
import io.prediction.data.storage.EngineManifest
import io.prediction.data.storage.Storage

import akka.actor._
import akka.event.Logging
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.github.nscala_time.time.Imports.DateTime
import com.twitter.bijection.Injection
import com.twitter.chill.KryoBase
import com.twitter.chill.KryoInjection
import com.twitter.chill.ScalaKryoInstantiator
import de.javakaffee.kryoserializers.ArraysAsListSerializer
import de.javakaffee.kryoserializers.SynchronizedCollectionsSerializer
import grizzled.slf4j.Logging
import io.prediction.workflow.JsonExtractorOption.JsonExtractorOption
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write
import spray.can.Http
import spray.http.MediaTypes._
import spray.http._
import spray.routing._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.future
import scala.language.existentials
import scala.util.Failure
import scala.util.Random
import scala.util.Success

import java.io.{Serializable, PrintWriter, StringWriter}
import java.util

class KryoInstantiator(classLoader: ClassLoader) extends ScalaKryoInstantiator {
  override def newKryo(): KryoBase = {
    val kryo = super.newKryo()
    kryo.setClassLoader(classLoader)
    SynchronizedCollectionsSerializer.registerSerializers(kryo)
    kryo.register(util.Arrays.asList("").getClass, new ArraysAsListSerializer())
    kryo
  }
}

object KryoInstantiator extends Serializable {
  def newKryoInjection : Injection[Any, Array[Byte]] = {
    val kryoInstantiator = new KryoInstantiator(getClass.getClassLoader)
    KryoInjection.instance(kryoInstantiator)
  }
}

case class ServerConfig(
  batch: String = "",
  engineInstanceId: String = "",
  engineId: Option[String] = None,
  engineVersion: Option[String] = None,
  ip: String = "0.0.0.0",
  port: Int = 8000,
  feedback: Boolean = false,
  eventServerIp: String = "0.0.0.0",
  eventServerPort: Int = 7070,
  accessKey: Option[String] = None,
  logUrl: Option[String] = None,
  logPrefix: Option[String] = None,
  logFile: Option[String] = None,
  verbose: Boolean = false,
  debug: Boolean = false,
  jsonExtractor: JsonExtractorOption = JsonExtractorOption.Both)

case class StartServer()
case class BindServer()
case class StopServer()
case class ReloadServer()
case class UpgradeCheck()

object CreateServer extends Logging {
  val actorSystem = ActorSystem("pio-server")
  val engineInstances = Storage.getMetaDataEngineInstances
  val engineManifests = Storage.getMetaDataEngineManifests
  val modeldata = Storage.getModelDataModels

  def main(args: Array[String]): Unit = {
    val parser = new scopt.OptionParser[ServerConfig]("CreateServer") {
      opt[String]("batch") action { (x, c) =>
        c.copy(batch = x)
      } text("Batch label of the deployment.")
      opt[String]("engineId") action { (x, c) =>
        c.copy(engineId = Some(x))
      } text("Engine ID.")
      opt[String]("engineVersion") action { (x, c) =>
        c.copy(engineVersion = Some(x))
      } text("Engine version.")
      opt[String]("ip") action { (x, c) =>
        c.copy(ip = x)
      }
      opt[Int]("port") action { (x, c) =>
        c.copy(port = x)
      } text("Port to bind to (default: 8000).")
      opt[String]("engineInstanceId") required() action { (x, c) =>
        c.copy(engineInstanceId = x)
      } text("Engine instance ID.")
      opt[Unit]("feedback") action { (_, c) =>
        c.copy(feedback = true)
      } text("Enable feedback loop to event server.")
      opt[String]("event-server-ip") action { (x, c) =>
        c.copy(eventServerIp = x)
      }
      opt[Int]("event-server-port") action { (x, c) =>
        c.copy(eventServerPort = x)
      } text("Event server port. Default: 7070")
      opt[String]("accesskey") action { (x, c) =>
        c.copy(accessKey = Some(x))
      } text("Event server access key.")
      opt[String]("log-url") action { (x, c) =>
        c.copy(logUrl = Some(x))
      }
      opt[String]("log-prefix") action { (x, c) =>
        c.copy(logPrefix = Some(x))
      }
      opt[String]("log-file") action { (x, c) =>
        c.copy(logFile = Some(x))
      }
      opt[Unit]("verbose") action { (x, c) =>
        c.copy(verbose = true)
      } text("Enable verbose output.")
      opt[Unit]("debug") action { (x, c) =>
        c.copy(debug = true)
      } text("Enable debug output.")
      opt[String]("json-extractor") action { (x, c) =>
        c.copy(jsonExtractor = JsonExtractorOption.withName(x))
      }
    }

    parser.parse(args, ServerConfig()) map { sc =>
      WorkflowUtils.modifyLogging(sc.verbose)
      engineInstances.get(sc.engineInstanceId) map { engineInstance =>
        val engineId = sc.engineId.getOrElse(engineInstance.engineId)
        val engineVersion = sc.engineVersion.getOrElse(
          engineInstance.engineVersion)
        engineManifests.get(engineId, engineVersion) map { manifest =>
          val engineFactoryName = engineInstance.engineFactory
          val upgrade = actorSystem.actorOf(Props(
            classOf[UpgradeActor],
            engineFactoryName))
          actorSystem.scheduler.schedule(
            0.seconds,
            1.days,
            upgrade,
            UpgradeCheck())
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

  def createServerActorWithEngine[TD, EIN, PD, Q, P, A](
    sc: ServerConfig,
    engineInstance: EngineInstance,
    engine: Engine[TD, EIN, PD, Q, P, A],
    engineLanguage: EngineLanguage.Value,
    manifest: EngineManifest): ActorRef = {

    val engineParams = engine.engineInstanceToEngineParams(engineInstance, sc.jsonExtractor)

    val kryo = KryoInstantiator.newKryoInjection

    val modelsFromEngineInstance =
      kryo.invert(modeldata.get(engineInstance.id).get.models).get.
      asInstanceOf[Seq[Any]]

    val sparkContext = WorkflowContext(
      batch = if (sc.batch == "") engineInstance.batch else sc.batch,
      executorEnv = engineInstance.env,
      mode = "Serving",
      sparkEnv = engineInstance.sparkConf)

    val models = engine.prepareDeploy(
      sparkContext,
      engineParams,
      engineInstance.id,
      modelsFromEngineInstance,
      params = WorkflowParams()
    )

    val algorithms = engineParams.algorithmParamsList.map { case (n, p) =>
      Doer(engine.algorithmClassMap(n), p)
    }

    val servingParamsWithName = engineParams.servingParams

    val serving = Doer(engine.servingClassMap(servingParamsWithName._1),
      servingParamsWithName._2)

    actorSystem.actorOf(
      Props(
        classOf[ServerActor[Q, P]],
        sc,
        engineInstance,
        engine,
        engineLanguage,
        manifest,
        engineParams.dataSourceParams._2,
        engineParams.preparatorParams._2,
        algorithms,
        engineParams.algorithmParamsList.map(_._2),
        models,
        serving,
        engineParams.servingParams._2))
  }
}

class UpgradeActor(engineClass: String) extends Actor {
  val log = Logging(context.system, this)
  implicit val system = context.system
  def receive: Actor.Receive = {
    case x: UpgradeCheck =>
      WorkflowUtils.checkUpgrade("deployment", engineClass)
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
  var retry = 3

  def undeploy(ip: String, port: Int): Unit = {
    val serverUrl = s"http://${ip}:${port}"
    log.info(
      s"Undeploying any existing engine instance at $serverUrl")
    try {
      val code = scalaj.http.Http(s"$serverUrl/stop").asString.code
      code match {
        case 200 => Unit
        case 404 => log.error(
          s"Another process is using $serverUrl. Unable to undeploy.")
        case _ => log.error(
          s"Another process is using $serverUrl, or an existing " +
          s"engine server is not responding properly (HTTP $code). " +
          "Unable to undeploy.")
      }
    } catch {
      case e: java.net.ConnectException =>
        log.warning(s"Nothing at $serverUrl")
      case _: Throwable =>
        log.error("Another process might be occupying " +
          s"$ip:$port. Unable to undeploy.")
    }
  }

  def receive: Actor.Receive = {
    case x: StartServer =>
      val actor = createServerActor(
        sc,
        engineInstance,
        engineFactoryName,
        manifest)
      currentServerActor = Some(actor)
      undeploy(sc.ip, sc.port)
      self ! BindServer()
    case x: BindServer =>
      currentServerActor map { actor =>
        IO(Http) ! Http.Bind(actor, interface = sc.ip, port = sc.port)
      } getOrElse {
        log.error("Cannot bind a non-existing server backend.")
      }
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
          manifest.version,
          engineInstance.engineVariant)
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
      if (retry > 0) {
        retry -= 1
        log.error(s"Bind failed. Retrying... ($retry more trial(s))")
        context.system.scheduler.scheduleOnce(1.seconds) {
          self ! BindServer()
        }
      } else {
        log.error("Bind failed. Shutting down.")
        system.shutdown
      }
  }

  def createServerActor(
      sc: ServerConfig,
      engineInstance: EngineInstance,
      engineFactoryName: String,
      manifest: EngineManifest): ActorRef = {
    val (engineLanguage, engineFactory) =
      WorkflowUtils.getEngine(engineFactoryName, getClass.getClassLoader)
    val engine = engineFactory()

    // EngineFactory return a base engine, which may not be deployable.
    if (!engine.isInstanceOf[Engine[_,_,_,_,_,_]]) {
      throw new NoSuchMethodException(s"Engine $engine is not deployable")
    }

    val deployableEngine = engine.asInstanceOf[Engine[_,_,_,_,_,_]]

    CreateServer.createServerActorWithEngine(
      sc,
      engineInstance,
      // engine,
      deployableEngine,
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
    val dataSourceParams: Params,
    val preparatorParams: Params,
    val algorithms: Seq[BaseAlgorithm[_, _, Q, P]],
    val algorithmsParams: Seq[Params],
    val models: Seq[Any],
    val serving: BaseServing[Q, P],
    val servingParams: Params) extends Actor with HttpService {
  val serverStartTime = DateTime.now
  val log = Logging(context.system, this)

  var requestCount: Int = 0
  var avgServingSec: Double = 0.0
  var lastServingSec: Double = 0.0

  def actorRefFactory: ActorContext = context

  def receive: Actor.Receive = runRoute(myRoute)

  val feedbackEnabled = if (args.feedback) {
    if (args.accessKey.isEmpty) {
      log.error("Feedback loop cannot be enabled because accessKey is empty.")
      false
    } else {
      true
    }
  } else false

  def remoteLog(logUrl: String, logPrefix: String, message: String): Unit = {
    implicit val formats = Utils.json4sDefaultFormats
    try {
      scalaj.http.Http(logUrl).postData(
        logPrefix + write(Map(
          "engineInstance" -> engineInstance,
          "message" -> message))).asString
    } catch {
      case e: Throwable =>
        log.error(s"Unable to send remote log: ${e.getMessage}")
    }
  }

  def getStackTraceString(e: Throwable): String = {
    val writer = new StringWriter()
    val printWriter = new PrintWriter(writer)
    e.printStackTrace(printWriter)
    writer.toString
  }

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
                dataSourceParams.toString,
                preparatorParams.toString,
                servingParams.toString,
                serverStartTime,
                feedbackEnabled,
                args.eventServerIp,
                args.eventServerPort,
                requestCount,
                avgServingSec,
                lastServingSec
              ).toString
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
              val servingStartTime = DateTime.now
              val jsonExtractorOption = args.jsonExtractor
              val queryTime = DateTime.now
              // Extract Query from Json
              val query = JsonExtractor.extract(
                jsonExtractorOption,
                queryString,
                algorithms.head.queryClass,
                algorithms.head.querySerializer,
                algorithms.head.gsonTypeAdapterFactories
              )
              // Deploy logic. First call Serving.supplement, then Algo.predict,
              // finally Serving.serve.
              val supplementedQuery = serving.supplementBase(query)
              // TODO: Parallelize the following.
              val predictions = algorithms.zipWithIndex.map { case (a, ai) =>
                a.predictBase(models(ai), supplementedQuery)
              }
              // Notice that it is by design to call Serving.serve with the
              // *original* query.
              val prediction = serving.serveBase(query, predictions)
              val predictionJValue = JsonExtractor.toJValue(
                jsonExtractorOption,
                prediction,
                algorithms.head.querySerializer,
                algorithms.head.gsonTypeAdapterFactories)
              val r = (predictionJValue,
                prediction,
                query)
              /** Handle feedback to Event Server
                * Send the following back to the Event Server
                * - appId
                * - engineInstanceId
                * - query
                * - prediction
                * - prId
                */
              val result = if (feedbackEnabled) {
                implicit val formats =
                  algorithms.headOption map { alg =>
                    alg.querySerializer
                  } getOrElse {
                    Utils.json4sDefaultFormats
                  }
                // val genPrId = Random.alphanumeric.take(64).mkString
                def genPrId: String = Random.alphanumeric.take(64).mkString
                val newPrId = r._2 match {
                  case id: WithPrId =>
                    val org = id.prId
                    if (org.isEmpty) genPrId else org
                  case _ => genPrId
                }

                // also save Query's prId as prId of this pio_pr predict events
                val queryPrId =
                  r._3 match {
                    case id: WithPrId =>
                      Map("prId" -> id.prId)
                    case _ =>
                      Map()
                  }
                val data = Map(
                  // "appId" -> dataSourceParams.asInstanceOf[ParamsWithAppId].appId,
                  "event" -> "predict",
                  "eventTime" -> queryTime.toString(),
                  "entityType" -> "pio_pr", // prediction result
                  "entityId" -> newPrId,
                  "properties" -> Map(
                    "engineInstanceId" -> engineInstance.id,
                    "query" -> r._3,
                    "prediction" -> r._2)) ++ queryPrId
                // At this point args.accessKey should be Some(String).
                val accessKey = args.accessKey.getOrElse("")
                val f: Future[Int] = future {
                  scalaj.http.Http(
                    s"http://${args.eventServerIp}:${args.eventServerPort}/" +
                    s"events.json?accessKey=$accessKey").postData(
                    write(data)).header(
                    "content-type", "application/json").asString.code
                }
                f onComplete {
                  case Success(code) => {
                    if (code != 201) {
                      log.error(s"Feedback event failed. Status code: $code."
                        + s"Data: ${write(data)}.")
                    }
                  }
                  case Failure(t) => {
                    log.error(s"Feedback event failed: ${t.getMessage}") }
                }
                // overwrite prId in predictedResult
                // - if it is WithPrId,
                //   then overwrite with new prId
                // - if it is not WithPrId, no prId injection
                if (r._2.isInstanceOf[WithPrId]) {
                  r._1 merge parse( s"""{"prId" : "$newPrId"}""")
                } else {
                  r._1
                }
              } else r._1

              // Bookkeeping
              val servingEndTime = DateTime.now
              lastServingSec =
                (servingEndTime.getMillis - servingStartTime.getMillis) / 1000.0
              avgServingSec =
                ((avgServingSec * requestCount) + lastServingSec) /
                (requestCount + 1)
              requestCount += 1

              respondWithMediaType(`application/json`) {
                complete(compact(render(result)))
              }
            } catch {
              case e: MappingException =>
                log.error(
                  s"Query '$queryString' is invalid. Reason: ${e.getMessage}")
                args.logUrl map { url =>
                  remoteLog(
                    url,
                    args.logPrefix.getOrElse(""),
                    s"Query:\n$queryString\n\nStack Trace:\n" +
                      s"${getStackTraceString(e)}\n\n")
                  }
                complete(StatusCodes.BadRequest, e.getMessage)
              case e: Throwable =>
                val msg = s"Query:\n$queryString\n\nStack Trace:\n" +
                  s"${getStackTraceString(e)}\n\n"
                log.error(msg)
                args.logUrl map { url =>
                  remoteLog(
                    url,
                    args.logPrefix.getOrElse(""),
                    msg)
                  }
                complete(StatusCodes.InternalServerError, msg)
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
