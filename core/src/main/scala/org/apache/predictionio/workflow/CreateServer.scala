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

import java.io.Serializable
import java.util.concurrent.TimeUnit

import akka.event.Logging
import com.github.nscala_time.time.Imports.DateTime
import com.twitter.bijection.Injection
import com.twitter.chill.{KryoBase, KryoInjection, ScalaKryoInstantiator}
import com.typesafe.config.ConfigFactory
import de.javakaffee.kryoserializers.SynchronizedCollectionsSerializer
import grizzled.slf4j.Logging
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.predictionio.authentication.KeyAuthentication
import org.apache.predictionio.controller.{Engine, Params, Utils, WithPrId}
import org.apache.predictionio.core.{BaseAlgorithm, BaseServing, Doer}
import org.apache.predictionio.data.storage.{EngineInstance, Storage}
import org.apache.predictionio.workflow.JsonExtractorOption.JsonExtractorOption
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write
import akka.actor._
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.directives._
import akka.http.scaladsl.server._
import akka.pattern.ask
import akka.util.Timeout
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import org.apache.predictionio.akkahttpjson4s.Json4sSupport._
import org.apache.predictionio.configuration.SSLConfiguration

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.existentials
import scala.util.{Failure, Random, Success}
import scalaj.http.HttpOptions

class KryoInstantiator(classLoader: ClassLoader) extends ScalaKryoInstantiator {
  override def newKryo(): KryoBase = {
    val kryo = super.newKryo()
    kryo.setClassLoader(classLoader)
    SynchronizedCollectionsSerializer.registerSerializers(kryo)
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
  engineVariant: String = "",
  env: Option[String] = None,
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


object CreateServer extends Logging {
  val actorSystem = ActorSystem("pio-server")
  val engineInstances = Storage.getMetaDataEngineInstances
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
      opt[String]("engine-variant") required() action { (x, c) =>
        c.copy(engineVariant = x)
      } text("Engine variant JSON.")
      opt[String]("ip") action { (x, c) =>
        c.copy(ip = x)
      }
      opt[String]("env") action { (x, c) =>
        c.copy(env = Some(x))
      } text("Comma-separated list of environmental variables (in 'FOO=BAR' " +
        "format) to pass to the Spark execution environment.")
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
        val engineFactoryName = engineInstance.engineFactory
        val master = actorSystem.actorOf(Props(
          classOf[MasterActor],
          sc,
          engineInstance,
          engineFactoryName),
        "master")
        implicit val timeout = Timeout(5.seconds)
        master ? StartServer()

        val f = actorSystem.whenTerminated
        Await.ready(f, Duration.Inf)

      } getOrElse {
        error(s"Invalid engine instance ID. Aborting server.")
      }
    }
  }

  def createPredictionServerWithEngine[TD, EIN, PD, Q, P, A](
    sc: ServerConfig,
    engineInstance: EngineInstance,
    engine: Engine[TD, EIN, PD, Q, P, A],
    engineLanguage: EngineLanguage.Value): PredictionServer[Q, P] = {

    val engineParams = engine.engineInstanceToEngineParams(
      engineInstance, sc.jsonExtractor)

    val kryo = KryoInstantiator.newKryoInjection

    val modelsFromEngineInstance =
      kryo.invert(modeldata.get(engineInstance.id).get.models).get.
      asInstanceOf[Seq[Any]]

    val batch = if (engineInstance.batch.nonEmpty) {
      s"${engineInstance.engineFactory} (${engineInstance.batch})"
    } else {
      engineInstance.engineFactory
    }

    val sparkContext = WorkflowContext(
      batch = batch,
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

    new PredictionServer(
      sc,
      engineInstance,
      engine,
      engineLanguage,
      engineParams.dataSourceParams._2,
      engineParams.preparatorParams._2,
      algorithms,
      engineParams.algorithmParamsList.map(_._2),
      models,
      serving,
      engineParams.servingParams._2,
      actorSystem)
  }
}


object EngineServerJson4sSupport {
  implicit val serialization = org.json4s.jackson.Serialization
  implicit def json4sFormats: Formats = DefaultFormats
}

class MasterActor (
    sc: ServerConfig,
    engineInstance: EngineInstance,
    engineFactoryName: String) extends Actor with KeyAuthentication with SSLConfiguration {

  val log = Logging(context.system, this)

  implicit val system = context.system
  implicit val materializer = ActorMaterializer()

  var currentServerBinding: Option[Future[ServerBinding]] = None
  var retry = 3
  val serverConfig = ConfigFactory.load("server.conf")
  val sslEnforced = serverConfig.getBoolean("org.apache.predictionio.server.ssl-enforced")
  val protocol = if (sslEnforced) "https://" else "http://"

  val https: Option[HttpsConnectionContext] = if(sslEnforced){
    val https = ConnectionContext.https(sslContext)
    Http().setDefaultServerHttpContext(https)
    Some(https)
  } else None

  def undeploy(ip: String, port: Int): Unit = {
    val serverUrl = s"${protocol}${ip}:${port}"
    log.info(
      s"Undeploying any existing engine instance at $serverUrl")
    try {
      val code = scalaj.http.Http(s"$serverUrl/stop")
        .option(HttpOptions.allowUnsafeSSL)
        .param(ServerKey.param, ServerKey.get)
        .method("POST").asString.code
      code match {
        case 200 => ()
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
      undeploy(sc.ip, sc.port)
      self ! BindServer()
    case x: BindServer =>
      currentServerBinding match {
        case Some(_) =>
          log.error("Cannot bind a non-existing server backend.")
        case None =>
          val server = createServer(sc, engineInstance, engineFactoryName)
          val route = server.createRoute()
          val binding = https match {
            case Some(https) =>
              Http().bindAndHandle(route, sc.ip, sc.port, connectionContext = https)
            case None =>
              Http().bindAndHandle(route, sc.ip, sc.port)
          }
          currentServerBinding = Some(binding)

          val serverUrl = s"${protocol}${sc.ip}:${sc.port}"
          log.info(s"Engine is deployed and running. Engine API is live at ${serverUrl}.")
      }
    case x: StopServer =>
      log.info(s"Stop server command received.")
      currentServerBinding match {
        case Some(f) =>
          f.flatMap { binding =>
            binding.unbind()
          }.foreach { _ =>
            system.terminate()
          }
        case None =>
          log.warning("No active server is running.")
      }
    case x: ReloadServer =>
      log.info("Reload server command received.")
        currentServerBinding match {
          case Some(f) =>
            f.flatMap { binding =>
              binding.unbind()
            }
            val latestEngineInstance =
              CreateServer.engineInstances.getLatestCompleted(
                engineInstance.engineId,
                engineInstance.engineVersion,
                engineInstance.engineVariant)
            latestEngineInstance map { lr =>
              val server = createServer(sc, lr, engineFactoryName)
              val route = server.createRoute()
              val binding = https match {
                case Some(https) =>
                  Http().bindAndHandle(route, sc.ip, sc.port, connectionContext = https)
                case None =>
                  Http().bindAndHandle(route, sc.ip, sc.port)
              }
              currentServerBinding = Some(binding)
            } getOrElse {
              log.warning(
                s"No latest completed engine instance for ${engineInstance.engineId} " +
                  s"${engineInstance.engineVersion}. Abort reloading.")
            }
          case None =>
            log.warning("No active server is running. Abort reloading.")
        }
  }

  def createServer(
      sc: ServerConfig,
      engineInstance: EngineInstance,
      engineFactoryName: String): PredictionServer[_, _] = {
    val (engineLanguage, engineFactory) =
      WorkflowUtils.getEngine(engineFactoryName, getClass.getClassLoader)
    val engine = engineFactory()

    // EngineFactory return a base engine, which may not be deployable.
    if (!engine.isInstanceOf[Engine[_,_,_,_,_,_]]) {
      throw new NoSuchMethodException(s"Engine $engine is not deployable")
    }

    val deployableEngine = engine.asInstanceOf[Engine[_,_,_,_,_,_]]

    CreateServer.createPredictionServerWithEngine(
      sc,
      engineInstance,
      // engine,
      deployableEngine,
      engineLanguage)
  }
}

class PredictionServer[Q, P](
    val args: ServerConfig,
    val engineInstance: EngineInstance,
    val engine: Engine[_, _, _, Q, P, _],
    val engineLanguage: EngineLanguage.Value,
    val dataSourceParams: Params,
    val preparatorParams: Params,
    val algorithms: Seq[BaseAlgorithm[_, _, Q, P]],
    val algorithmsParams: Seq[Params],
    val models: Seq[Any],
    val serving: BaseServing[Q, P],
    val servingParams: Params,
    val system: ActorSystem) extends KeyAuthentication {

  val log = Logging(system, getClass)
  val serverStartTime = DateTime.now

  var requestCount: Int = 0
  var avgServingSec: Double = 0.0
  var lastServingSec: Double = 0.0

  implicit val timeout = Timeout(5, TimeUnit.SECONDS)

  val pluginsActorRef =
    system.actorOf(Props(classOf[PluginsActor], args.engineVariant), "PluginsActor")

  val pluginContext = EngineServerPluginContext(log, args.engineVariant)

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

  def authenticate[T](authenticator: RequestContext => Future[Either[Rejection, T]]):
      AuthenticationDirective[T] = {
    extractRequestContext.flatMap { requestContext =>
      onSuccess(authenticator(requestContext)).flatMap {
        case Right(x) => provide(x)
        case Left(x)  => reject(x): Directive1[T]
      }
    }
  }

  def createRoute(): Route = {
    val myRoute =
      path("") {
        get {
          complete(HttpResponse(entity = HttpEntity(
            `text/html(UTF-8)`,
            html.index(
              args,
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
          )))
        }
      } ~
      path("queries.json") {
        post {
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
              val queryJValue = JsonExtractor.toJValue(
                jsonExtractorOption,
                query,
                algorithms.head.querySerializer,
                algorithms.head.gsonTypeAdapterFactories)
              // Deploy logic. First call Serving.supplement, then Algo.predict,
              // finally Serving.serve.
              val supplementedQuery = serving.supplementBase(query)
              // TODO: Parallelize the following.
              val predictions = algorithms.zip(models).map { case (a, m) =>
                a.predictBase(m, supplementedQuery)
              }
              // Notice that it is by design to call Serving.serve with the
              // *original* query.
              val prediction = serving.serveBase(query, predictions)
              val predictionJValue = JsonExtractor.toJValue(
                jsonExtractorOption,
                prediction,
                algorithms.head.querySerializer,
                algorithms.head.gsonTypeAdapterFactories)
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
                val newPrId = prediction match {
                  case id: WithPrId =>
                    val org = id.prId
                    if (org.isEmpty) genPrId else org
                  case _ => genPrId
                }

                // also save Query's prId as prId of this pio_pr predict events
                val queryPrId =
                  query match {
                    case id: WithPrId =>
                      Map("prId" -> id.prId)
                    case _ =>
                      Map.empty
                  }
                val data = Map(
                  // "appId" -> dataSourceParams.asInstanceOf[ParamsWithAppId].appId,
                  "event" -> "predict",
                  "eventTime" -> queryTime.toString(),
                  "entityType" -> "pio_pr", // prediction result
                  "entityId" -> newPrId,
                  "properties" -> Map(
                    "engineInstanceId" -> engineInstance.id,
                    "query" -> query,
                    "prediction" -> prediction)) ++ queryPrId
                // At this point args.accessKey should be Some(String).
                val accessKey = args.accessKey.getOrElse("")
                val f: Future[Int] = Future {
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
                if (prediction.isInstanceOf[WithPrId]) {
                  predictionJValue merge parse(s"""{"prId" : "$newPrId"}""")
                } else {
                  predictionJValue
                }
              } else predictionJValue

              val pluginResult =
                pluginContext.outputBlockers.values.foldLeft(result) { case (r, p) =>
                  p.process(engineInstance, queryJValue, r, pluginContext)
                }
              pluginsActorRef ! (engineInstance, queryJValue, result)

              // Bookkeeping
              val servingEndTime = DateTime.now
              lastServingSec =
                (servingEndTime.getMillis - servingStartTime.getMillis) / 1000.0
              avgServingSec =
                ((avgServingSec * requestCount) + lastServingSec) /
                (requestCount + 1)
              requestCount += 1

              complete(compact(render(pluginResult)))

            } catch {
              case e: MappingException =>
                val msg = s"Query:\n$queryString\n\nStack Trace:\n" +
                  s"${ExceptionUtils.getStackTrace(e)}\n\n"
                log.error(msg)
                args.logUrl map { url =>
                  remoteLog(
                    url,
                    args.logPrefix.getOrElse(""),
                    msg)
                  }
                complete(StatusCodes.BadRequest, e.getMessage)
              case e: Throwable =>
                val msg = s"Query:\n$queryString\n\nStack Trace:\n" +
                  s"${ExceptionUtils.getStackTrace(e)}\n\n"
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
      } ~
      path("reload") {
        authenticate(withAccessKeyFromFile) { request =>
          post {
            system.actorSelection("/user/master") ! ReloadServer()
            complete("Reloading...")
          }
        }
      } ~
      path("stop") {
        authenticate(withAccessKeyFromFile) { request =>
          post {
            system.scheduler.scheduleOnce(1.seconds) {
              system.actorSelection("/user/master") ! StopServer()
            }
            complete("Shutting down...")
          }
        }
      } ~
      pathPrefix("assets") {
        getFromResourceDirectory("assets")
      } ~
      path("plugins.json") {
        import EngineServerJson4sSupport._
        get {
          complete(
            Map("plugins" -> Map(
              "outputblockers" -> pluginContext.outputBlockers.map { case (n, p) =>
                n -> Map(
                  "name"        -> p.pluginName,
                  "description" -> p.pluginDescription,
                  "class"       -> p.getClass.getName,
                  "params"      -> pluginContext.pluginParams(p.pluginName))
              },
              "outputsniffers" -> pluginContext.outputSniffers.map { case (n, p) =>
                n -> Map(
                  "name"        -> p.pluginName,
                  "description" -> p.pluginDescription,
                  "class"       -> p.getClass.getName,
                  "params"      -> pluginContext.pluginParams(p.pluginName))
              }
            ))
          )
        }
      } ~
      path("plugins" / Segments) { segments =>
        import EngineServerJson4sSupport._
        get {
          val pluginArgs = segments.drop(2)
          val pluginType = segments(0)
          val pluginName = segments(1)
          pluginType match {
            case EngineServerPlugin.outputBlocker =>
              complete(HttpResponse(entity = HttpEntity(
                  `application/json`,
                  pluginContext.outputBlockers(pluginName).handleREST(pluginArgs))))

            case EngineServerPlugin.outputSniffer =>
              complete(pluginsActorRef ? PluginsActor.HandleREST(
                pluginName = pluginName,
                pluginArgs = pluginArgs) map { json =>
                HttpResponse(entity = HttpEntity(
                  `application/json`,
                  json.asInstanceOf[String]
                ))
              })
          }
        }
      }

    myRoute
  }
}
