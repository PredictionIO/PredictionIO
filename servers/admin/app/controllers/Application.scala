package controllers

import io.prediction.commons.Config
import io.prediction.commons.settings._
import io.prediction.commons.modeldata.ItemRecScores
import io.prediction.commons.appdata.{ Users, Items, U2IActions }
import io.prediction.output.AlgoOutputSelector

import Helper.{ algoToJson, offlineEvalMetricToJson }
import Helper.{ dateTimeToString, algoParamToString, offlineEvalSplitterParamToString }
import Helper.{ getSimEvalStatus, getOfflineTuneStatus, createOfflineEval }

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data.validation.{ Constraints }
import play.api.i18n.{ Messages, Lang }
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json.toJson
import play.api.libs.json.{ JsNull, JsArray, Json, JsValue, Writes, JsObject }
import play.api.libs.ws.WS
import play.api.Play.current
import play.api.http

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.util.Random

import com.github.nscala_time.time.Imports._
import org.apache.commons.codec.digest.DigestUtils

import Forms._

/*
 * TODO:
 * - decodeURIComponent any GET custom param
 */

/*
 * Backend of ControlPanel.
 * Pure REST APIs in JSON.
 */
object Application extends Controller {
  /** PredictionIO Commons settings*/
  val config = new Config()
  val users = config.getSettingsUsers()
  val apps = config.getSettingsApps()
  val engines = config.getSettingsEngines()
  val engineInfos = config.getSettingsEngineInfos()
  val algos = config.getSettingsAlgos()
  val algoInfos = config.getSettingsAlgoInfos()
  val offlineEvalMetricInfos = config.getSettingsOfflineEvalMetricInfos()
  val offlineEvalSplitterInfos = config.getSettingsOfflineEvalSplitterInfos()
  val offlineEvals = config.getSettingsOfflineEvals()
  val offlineEvalMetrics = config.getSettingsOfflineEvalMetrics()
  val offlineEvalResults = config.getSettingsOfflineEvalResults()
  val offlineEvalSplitters = config.getSettingsOfflineEvalSplitters()
  val offlineTunes = config.getSettingsOfflineTunes()
  val paramGens = config.getSettingsParamGens()

  /** PredictionIO Commons modeldata */
  val itemRecScores = config.getModeldataItemRecScores()
  val itemSimScores = config.getModeldataItemSimScores()

  /** PredictionIO Commons modeldata */
  val trainingItemRecScores = config.getModeldataTrainingItemRecScores()
  val trainingItemSimScores = config.getModeldataTrainingItemSimScores()

  /** PredictionIO Commons appdata */
  val appDataUsers = config.getAppdataUsers()
  val appDataItems = config.getAppdataItems()
  val appDataU2IActions = config.getAppdataU2IActions()

  /** PredictionIO Commons training set appdata */
  val trainingSetUsers = config.getAppdataTrainingUsers()
  val trainingSetItems = config.getAppdataTrainingItems()
  val trainingSetU2IActions = config.getAppdataTrainingU2IActions()

  /** PredictionIO Commons validation set appdata */
  val validationSetUsers = config.getAppdataValidationUsers()
  val validationSetItems = config.getAppdataValidationItems()
  val validationSetU2IActions = config.getAppdataValidationU2IActions()

  /** PredictionIO Commons test set appdata */
  val testSetUsers = config.getAppdataTestUsers()
  val testSetItems = config.getAppdataTestItems()
  val testSetU2IActions = config.getAppdataTestU2IActions()

  /** Scheduler setting */
  val settingsSchedulerUrl = config.settingsSchedulerUrl

  /** PredictionIO Output */
  val algoOutputSelector = new AlgoOutputSelector(algos)

  /** misc */
  val nameRegex = """\b[a-zA-Z][a-zA-Z0-9_-]*\b""".r

  /** Play Framework security */
  def username(request: RequestHeader) = request.session.get(Security.username)

  def onUnauthorized(request: RequestHeader) = Forbidden(toJson(Map("message" -> toJson("Haven't signed in yet."))))

  def withAuth(f: => String => Request[AnyContent] => Result) = {
    Security.Authenticated(username, onUnauthorized) { user =>
      Action(request => f(user)(request))
    }
  }

  def withAuthAsync(f: => String => Request[AnyContent] => Future[SimpleResult]) = {
    Security.Authenticated(username, onUnauthorized) { user =>
      Action.async(request => f(user)(request))
    }
  }

  object WithUser {
    def apply(f: User => Request[AnyContent] => SimpleResult) = async { user =>
      implicit request =>
        Future.successful(f(user)(request))
    }

    def async(f: User => Request[AnyContent] => Future[SimpleResult]) = withAuthAsync { username =>
      implicit request =>
        users.getByEmail(username).map { user =>
          f(user)(request)
        }.getOrElse(Future.successful(onUnauthorized(request)))
    }
  }

  object WithApp {
    def apply(appid: Int)(f: (User, App) => Request[AnyContent] => SimpleResult) = async(appid) { (user, app) =>
      implicit request =>
        Future.successful(f(user, app)(request))
    }

    def async(appid: Int)(f: (User, App) => Request[AnyContent] => Future[SimpleResult]) = WithUser.async { user =>
      implicit request =>
        apps.getByIdAndUserid(appid, user.id).map { app =>
          f(user, app)(request)
        }.getOrElse(Future.successful(NotFound(Json.obj("message" -> s"Invalid appid ${appid}."))))
    }
  }

  object WithEngine {
    def apply(appid: Int, engineid: Int)(f: (User, App, Engine) => Request[AnyContent] => SimpleResult) = async(appid, engineid) {
      (user, app, eng) =>
        implicit request =>
          Future.successful(f(user, app, eng)(request))
    }

    def async(appid: Int, engineid: Int)(f: (User, App, Engine) => Request[AnyContent] => Future[SimpleResult]) = WithApp.async(appid) {
      (user, app) =>
        implicit request =>
          engines.getByIdAndAppid(engineid, appid).map { eng =>
            f(user, app, eng)(request)
          }.getOrElse(Future.successful(NotFound(Json.obj("message" -> s"Invalid engineid ${engineid}."))))
    }
  }

  object WithAlgo {
    def apply(appid: Int, engineid: Int, algoid: Int)(f: (User, App, Engine, Algo) => Request[AnyContent] => SimpleResult) = async(appid, engineid, algoid) {
      (user, app, eng, algo) =>
        implicit request =>
          Future.successful(f(user, app, eng, algo)(request))
    }

    def async(appid: Int, engineid: Int, algoid: Int)(f: (User, App, Engine, Algo) => Request[AnyContent] => Future[SimpleResult]) = WithEngine.async(appid, engineid) {
      (user, app, eng) =>
        implicit request =>
          algos.getByIdAndEngineid(algoid, engineid).map { algo =>
            f(user, app, eng, algo)(request)
          }.getOrElse(Future.successful(NotFound(Json.obj("message" -> s"Invalid algoid ${algoid}."))))
    }
  }

  object WithOfflineEval {
    def apply(appid: Int, engineid: Int, offlineevalid: Int)(f: (User, App, Engine, OfflineEval) => Request[AnyContent] => SimpleResult) = async(appid, engineid, offlineevalid) {
      (user, app, eng, eval) =>
        implicit request =>
          Future.successful(f(user, app, eng, eval)(request))
    }

    def async(appid: Int, engineid: Int, offlineevalid: Int)(f: (User, App, Engine, OfflineEval) => Request[AnyContent] => Future[SimpleResult]) = WithEngine.async(appid, engineid) {
      (user, app, eng) =>
        implicit request =>
          offlineEvals.getByIdAndEngineid(offlineevalid, engineid).map { eval =>
            f(user, app, eng, eval)(request)
          }.getOrElse(Future.successful(NotFound(Json.obj("message" -> s"Invalid offlineevalid ${offlineevalid}."))))
    }
  }

  private def md5password(password: String) = DigestUtils.md5Hex(password)

  /** Appkey Generation */
  def randomAlphanumeric(n: Int): String = {
    Random.alphanumeric.take(n).mkString
  }

  def showWeb() = Action {
    Ok(views.html.Web.index())
  }

  /* Serve Engines/Algorithms Static Files (avoid PlayFramework's Assets cache problem during development)*/
  def enginebase(path: String) = Action {
    Ok.sendFile(new java.io.File(Play.application.path, "/enginebase/" + path))
    //TODO: Fix Content-Disposition
  }

  def redirectToWeb = Action {
    Redirect("web/")
  }

  /* case class to json conversion */
  implicit val userWrites = new Writes[User] {
    /* note: do not return password */
    def writes(u: User): JsValue = {
      Json.obj(
        "id" -> u.id,
        "username" -> (u.firstName + u.lastName.map(" " + _).getOrElse("")),
        "email" -> u.email
      )
    }
  }

  /**
   * Authenticates user
   *
   * {{{
   * POST
   * JSON parameters:
   *   {
   *     "email" : <string>,
   *     "password" : <string>,
   *     "remember" : <optional string "on">
   *   }
   * JSON response:
   *   {
   *     "id" : <string>,
   *     "username" : <string>,
   *     "email" : <string>
   *   }
   * }}}
   *
   */
  def signin = Action { implicit request =>
    val loginForm = Form(
      tuple(
        "email" -> text,
        "password" -> text,
        "remember" -> optional(text)
      ) verifying ("Invalid email or password", result => result match {
          case (email, password, remember) => users.authenticateByEmail(email, md5password(password)) map { _ => true } getOrElse false
        })
    )

    loginForm.bindFromRequest.fold(
      formWithErrors => Forbidden(toJson(Map("message" -> toJson("Incorrect Email or Password.")))),
      form => {
        users.getByEmail(form._1).map(user =>
          Ok(Json.toJson(user)).withSession(Security.username -> user.email)
        ).getOrElse(
          InternalServerError(Json.obj("message" -> "Could not find your user account."))
        )
      }
    )
  }

  /**
   * Signs out
   *
   * {{{
   * POST
   * JSON parameters:
   *   None
   * JSON response:
   *   None
   * }}}
   */
  def signout = Action {
    Ok.withNewSession
  }

  /**
   * Returns authenticated user info
   * (from session cookie)
   *
   * {{{
   * GET
   * JSON parameters:
   *   None
   * JSON response:
   *   If authenticated
   *   OK
   *   {
   *     "id" : <string>,
   *     "username" : <string>,
   *     "email": <string>
   *   }
   *
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   * }}}
   */
  def getAuth = WithUser { user =>
    implicit request =>
      Ok(Json.toJson(user))
  }

  /**
   * Returns list of apps of the authenticated user
   *
   * {{{
   * GET
   * JSON parameters:
   *   None
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If no app:
   *   No Content
   *
   *   If apps are found:
   *   OK
   *   [ { "id" : <appid int>,  "appname" : <string> },
   *     ...
   *   ]
   *
   * }}}
   */
  def getApplist = WithUser { user =>
    implicit request =>

      val userApps = apps.getByUserid(user.id)
      if (!userApps.hasNext) NoContent
      else {
        Ok(JsArray(userApps.map { app =>
          Json.obj("id" -> app.id, "appname" -> app.display)
        }.toSeq))
      }
  }

  /**
   * Returns the app details of this appid
   *
   * {{{
   * GET
   * JSON parameters:
   *   None
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If app is not found:
   *   NotFound
   *   {
   *     "message" : "Invalid appid."
   *   }
   *
   *   If app is found:
   *   Ok
   *   {
   *     "id" : <appid int>,
   *     "updatedtime" : <string>,
   *     "userscount": <num of users int>
   *     "itemscount": <num of items int>
   *     "u2icount": <num of u2i Actions int>
   *     "apiurl": <url of API server, string>
   *     "appkey": <string>
   *   }
   * }}}
   *
   * @param id the App ID
   */
  def getAppDetails(id: Int) = WithApp(id) { (user, app) =>
    implicit request =>
      val numUsers = appDataUsers.countByAppid(app.id)
      val numItems = appDataItems.countByAppid(app.id)
      val numU2IActions = appDataU2IActions.countByAppid(app.id)
      Ok(Json.obj(
        "id" -> app.id,
        "updatedtime" -> dateTimeToString(DateTime.now),
        "userscount" -> numUsers,
        "itemscount" -> numItems,
        "u2icount" -> numU2IActions,
        "apiurl" -> "http://yourhost.com:123/appid12",
        "appkey" -> app.appkey
      ))
  }

  /**
   * Returns an app
   *
   * {{{
   * GET
   * JSON parameters:
   *   None
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If app is found:
   *   Ok
   *   {
   *     "id" : <appid int>
   *     "appname" : <string>
   *   }
   *
   * }}}
   *
   * @param id the App ID
   */
  def getApp(id: Int) = WithApp(id) { (user, app) =>
    implicit request =>
      Ok(Json.obj(
        "id" -> app.id,
        "appname" -> app.display
      ))
  }

  /**
   * Create an app
   *
   * {{{
   * POST
   * JSON Parameters:
   *   {
   *     "appname" : <the new app name. string>
   *   }
   * JSON Response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If creation failed:
   *   BadRequest
   *   {
   *     "message" : "Invalid character for app name."
   *    }
   *
   *   If app is created:
   *   OK
   *   {
   *     "id" : <the appid of the created app. int>,
   *     "appname" : <string>
   *   }
   * }}}
   *
   */
  def createApp = WithUser { user =>
    implicit request =>

      val appForm = Form(single(
        "appname" -> nonEmptyText
      ))

      appForm.bindFromRequest.fold(
        formWithError => {
          val msg = formWithError.errors(0).message // extract 1st error message only
          BadRequest(toJson(Map("message" -> toJson(msg))))
        },
        formData => {
          val an = formData
          val appid = apps.insert(App(
            id = 0,
            userid = user.id,
            appkey = randomAlphanumeric(64),
            display = an,
            url = None,
            cat = None,
            desc = None,
            timezone = "UTC"
          ))
          Logger.info("Create app ID " + appid)

          Ok(Json.obj(
            "id" -> appid,
            "appname" -> an
          ))
        }
      )
  }

  /**
   * Remove an app
   *
   * {{{
   * DELETE
   * JSON Parameters:
   *   None
   * JSON Response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If not found:
   *   NotFound
   *   {
   *     "message" : <error message>
   *   }
   *
   *   If error:
   *   InternalServerError
   *   {
   *     "message" : <error message>
   *   }
   *
   *   If deleted successfully:
   *   Ok
   * }}}
   *
   * @param id the App ID
   */
  def removeApp(id: Int) = WithApp.async(id) { (user, app) =>
    implicit request =>

      // don't delete if there is any deployed aglo, sim eval or offline tune pending
      val appEngines = engines.getByAppid(app.id).toList

      val enginesDeployed = appEngines.filter(eng =>
        !(algos.getDeployedByEngineid(eng.id).isEmpty)
      )
      val msgDeployed = "There are deployed algorithms in engines: " + enginesDeployed.map(_.name).mkString(", ") + ". Please undeploy them before delete this app."

      val enginesSimEvals = appEngines.filter(eng =>
        !(Helper.getSimEvalsByEngineid(eng.id).filter(Helper.isPendingSimEval(_)).isEmpty)
      )
      val msgSimEvals = "There are running simulated evaluations in engines: " + enginesSimEvals.map(_.name).mkString(", ") + ". Please stop and delete them before delete this app."

      val enginesOfflineTunes = appEngines.filter(eng =>
        !(offlineTunes.getByEngineid(eng.id).filter(Helper.isPendingOfflineTune(_)).isEmpty)
      )
      val msgOfflineTunes = "There are auto-tuning algorithms in engines: " + enginesOfflineTunes.map(_.name).mkString(", ") + ". Please stop and delete them before delete this app."

      val runningEngines = List(
        (enginesDeployed, msgDeployed),
        (enginesSimEvals, msgSimEvals),
        (enginesOfflineTunes, msgOfflineTunes)
      ).filter { case (x, y) => (!x.isEmpty) }

      if (!runningEngines.isEmpty) {
        val msg = runningEngines.map { case (x, y) => y }.mkString(" ")
        concurrent.Future(Forbidden(Json.obj("message" -> msg)))
      } else {

        val timeout = play.api.libs.concurrent.Promise.timeout("Scheduler is unreachable. Giving up.", concurrent.duration.Duration(10, concurrent.duration.MINUTES))
        val delete = Helper.deleteAppScheduler(app.id)

        concurrent.Future.firstCompletedOf(Seq(delete, timeout)).map {
          case r: SimpleResult => {
            if (r.header.status == http.Status.OK) {
              Helper.deleteApp(id, user.id, keepSettings = false)
            }
            r
          }
          case t: String => InternalServerError(Json.obj("message" -> t))
        }
      }
  }

  /**
   * Erase appdata of this appid
   *
   * {{{
   * POST
   * JSON Parameters:
   *   None
   * JSON Response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If erased successfully:
   *   Ok
   * }}}
   *
   * @param id the App ID
   */
  def eraseAppData(id: Int) = WithApp.async(id) { (user, app) =>
    implicit request =>

      val timeout = play.api.libs.concurrent.Promise.timeout("Scheduler is unreachable. Giving up.", concurrent.duration.Duration(10, concurrent.duration.MINUTES))
      val delete = Helper.deleteAppScheduler(app.id)

      concurrent.Future.firstCompletedOf(Seq(delete, timeout)).map {
        case r: SimpleResult => {
          if (r.header.status == http.Status.OK) {
            Helper.deleteApp(id, user.id, keepSettings = true)
          }
          r
        }
        case t: String => InternalServerError(Json.obj("message" -> t))
      }
  }

  /**
   * Returns a list of available engine infos in the system
   *
   * {{{
   * GET
   * JSON Parameters:
   *   None
   * JSON Response:
   *   Ok
   *   [ { "id" : <engine info id stirng>,
   *       "engineinfoname" : <name of the engine info>,
   *       "description": <description in html string>
   *     },
   *     ...
   *   ]
   * }}}
   */
  def getEngineInfoList = Action {
    Ok(JsArray(engineInfos.getAll() map {
      eng =>
        Json.obj(
          "id" -> eng.id,
          "engineinfoname" -> eng.name,
          "description" -> eng.description
        )
    }))
  }

  /**
   * Returns a list of available algo infos of a specific engine info
   *
   * {{{
   * GET
   * JSON Parameters:
   *   None
   * JSON Response:
   *   If the engine info id is not found:
   *   InternalServerError
   *   {
   *     "message" : "Invalid EngineInfo ID."
   *   }
   *
   *   If found:
   *   Ok
   *   { "engineinfoname" : <the name of the engine info>,
   *     "algotypelist" : [ { "id" : <algo info id>,
   *                          "algoinfoname" : <name of the algo info>,
   *                          "description" : <string>,
   *                          "req" : [<technology requirement string>],
   *                          "datareq" : [<data requirement string>]
   *                        }, ...
   *                      ]
   *   }
   * }}}
   *
   * @param id the engine info id
   */
  def getEngineInfoAlgoInfoList(id: String) = Action {
    engineInfos.get(id) map { engineInfo =>
      Ok(Json.obj(
        "engineinfoname" -> engineInfo.name,
        "algotypelist" -> JsArray(
          algoInfos.getByEngineInfoId(id).map { algoInfo =>
            Json.obj(
              "id" -> algoInfo.id,
              "algoinfoname" -> algoInfo.name,
              "description" -> algoInfo.description.getOrElse[String](""),
              "req" -> Json.toJson(algoInfo.techreq),
              "datareq" -> Json.toJson(algoInfo.datareq)
            )
          }
        )
      ))
    } getOrElse InternalServerError(Json.obj("message" -> s"Invalid EngineInfo ID: ${id}."))
  }

  /**
   * Returns a list of available metric infos of a specific engine info
   *
   * {{{
   * GET
   * JSON Parameters:
   *   None
   * JSON Response:
   *   If the engine info id is not found:
   *   InternalServerError
   *   {
   *     "message" : "Invalid EngineInfo ID."
   *   }
   *
   *   If found:
   *   Ok
   *   { "engineinfoname" : <the name of the engine info>,
   *     "defaultmetric" : <default metric info id>,
   *     "metricslist" : [ { "id" : <metric info id>,
   *                         "name" : <short name of the metric info>,
   *                         "description" : <long name of the metric info>,
   *                       }, ...
   *                     ]
   *   }
   * }}}
   *
   * @param id the engine info id
   */
  def getEngineInfoMetricInfoList(id: String) = Action {
    engineInfos.get(id) map { engInfo =>
      val metrics = offlineEvalMetricInfos.getByEngineinfoid(engInfo.id).map { m =>
        Json.obj(
          "id" -> m.id,
          "name" -> m.name,
          "description" -> m.description
        )
      }
      Ok(Json.obj(
        "engineinfoname" -> engInfo.name,
        "defaultmetric" -> engInfo.defaultofflineevalmetricinfoid,
        "metricslist" -> JsArray(metrics)
      ))
    } getOrElse {
      InternalServerError(Json.obj("message" -> s"Invalid engineinfo ID: ${id}."))
    }
  }

  /**
   * Returns a list of available splitter infos of a specific engine info
   *
   * {{{
   * GET
   * JSON Parameters:
   *   None
   * JSON Response:
   *   If the engine info id is not found:
   *   InternalServerError
   *   {
   *     "message" : "Invalid EngineInfo ID."
   *   }
   *
   *   If found:
   *   Ok
   *   { "engineinfoname" : <the name of the engine info>,
   *     "defaultsplitter": <default splitter info id>,
   *     "splitterlist" : [ { "id" : <splitter info id>,
   *                          "name" : <name of splitter>,
   *                          "description" : <splitter description>,
   *                       }, ...
   *                     ]
   *   }
   * }}}
   *
   * @param id the engine info id
   */
  def getEngineInfoSplitterInfoList(id: String) = Action {
    engineInfos.get(id).map { engInfo =>
      val splitters = offlineEvalSplitterInfos.getByEngineinfoid(engInfo.id).map { m =>
        Json.obj(
          "id" -> m.id,
          "name" -> m.name,
          "description" -> m.description
        )
      }
      Ok(Json.obj(
        "engineinfoname" -> engInfo.name,
        "defaultsplitter" -> engInfo.defaultofflineevalsplitterinfoid,
        "splitterlist" -> JsArray(splitters)
      ))
    }.getOrElse {
      InternalServerError(Json.obj("message" -> s"Invalid engineinfo ID: ${id}."))
    }
  }

  /**
   * Returns list of engines of this appid
   *
   * {{{
   * GET
   * JSON parameters:
   *   None
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If no engine:
   *   NoContent
   *
   *   If engines found:
   *   Ok
   *   {
   *     "id" : <appid int>,
   *     "enginelist" : [ { "id" : <engineid int>, "enginename" : <string>, "engineinfoid" : <string> },
   *                       ....,
   *                      { "id" : <engineid int>, "enginename" : <string>, "engineinfoid" : <string> } ]
   *
   *   }
   * }}}
   *
   * @param id the App ID
   */
  def getAppEnginelist(appid: Int) = WithApp(appid) { (user, app) =>
    implicit request =>

      val appEngines = engines.getByAppid(appid)

      if (!appEngines.hasNext) NoContent
      else
        Ok(Json.obj(
          "id" -> appid,
          "enginelist" -> JsArray(appEngines.map { eng =>
            Json.obj("id" -> eng.id, "enginename" -> eng.name, "engineinfoid" -> eng.infoid)
          }.toSeq)
        ))
  }

  /**
   * Returns the engine of this engineid
   *
   * {{{
   * GET
   * JSON Parameters:
   *   None
   * JSON Response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If engine not found:
   *   NotFound
   *   {
   *     "message" : "Invalid app id or engine id."
   *   }
   *
   *   If engine found:
   *   Ok
   *   {
   *     "id" : <engine id>,
   *     "engineinfoid" : <engine info id>,
   *     "enginename" : <engine name>,
   *     "enginestatus" : <engine status>
   *   }
   * }}}
   * @note engine status:
   *   noappdata
   *   nodeployedalgo
   *   firsttraining
   *   nomodeldata
   *   nomodeldatanoscheduler
   *   training
   *   running
   *   runningnoscheduler
   *
   * @param appid the App ID
   * @param id the engine ID
   */
  def getEngine(appid: Int, id: Int) = WithEngine(appid, id) { (user, app, eng) =>
    implicit request =>

      val modelDataExist: Boolean = eng.infoid match {
        case "itemrec" => try { itemRecScores.existByAlgo(algoOutputSelector.itemRecAlgoSelection(eng)) } catch { case e: RuntimeException => false }
        case "itemsim" => try { itemSimScores.existByAlgo(algoOutputSelector.itemSimAlgoSelection(eng)) } catch { case e: RuntimeException => false }
        case _ => false
      }
      val deployedAlgos = algos.getDeployedByEngineid(eng.id)
      val hasDeployedAlgo = deployedAlgos.hasNext
      val algo = if (deployedAlgos.hasNext) Some(deployedAlgos.next()) else None
      val engineStatus: String =
        if (appDataUsers.countByAppid(eng.appid) == 0 && appDataItems.countByAppid(eng.appid) == 0 && appDataU2IActions.countByAppid(eng.appid) == 0)
          "noappdata"
        else if (!hasDeployedAlgo)
          "nodeployedalgo"
        else if (!modelDataExist)
          try {
            (Await.result(WS.url(s"${settingsSchedulerUrl}/apps/${eng.appid}/engines/${eng.id}/algos/${algo.get.id}/status").get(), scala.concurrent.duration.Duration(5, SECONDS)).json \ "status").as[String] match {
              case "jobrunning" => "firsttraining"
              case _ => "nomodeldata"
            }
          } catch {
            case e: java.net.ConnectException => "nomodeldatanoscheduler"
          }
        else
          try {
            (Await.result(WS.url(s"${settingsSchedulerUrl}/apps/${eng.appid}/engines/${eng.id}/algos/${algo.get.id}/status").get(), scala.concurrent.duration.Duration(5, SECONDS)).json \ "status").as[String] match {
              case "jobrunning" => "training"
              case _ => "running"
            }
          } catch {
            case e: java.net.ConnectException => "runningnoscheduler"
          }
      Ok(Json.obj(
        "id" -> eng.id, // engine id
        "engineinfoid" -> eng.infoid,
        "appid" -> eng.appid,
        "enginename" -> eng.name,
        "enginestatus" -> engineStatus))
  }

  /**
   * Creates an Engine
   *
   * {{{
   * POST
   * JSON parameters:
   *   {
   *     "engineinfoid" : <engine info id>,
   *     "enginename" : <engine name>
   *   }
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   if invalid appid:
   *   NotFound
   *   {
   *     "message" : <error message>
   *   }
   *
   *   If bad param:
   *   BadRequest
   *   {
   *     "message" : <error message>
   *   }
   *
   *   If created:
   *   Ok
   *   {
   *     "id" : <new engine id>,
   *     "engineinfoid" : <engine info id>,
   *     "appid" : <app id>,
   *     "enginename" : <engine name>
   *   }
   *
   * }}}
   *
   * @param appid the App ID
   */
  def createEngine(appid: Int) = WithApp(appid) { (user, app) =>
    implicit request =>

      val supportedEngineTypes: Seq[String] = engineInfos.getAll() map { _.id }
      val enginenameConstraint = Constraints.pattern(nameRegex, "constraint.enginename", "Engine names should only contain alphanumerical characters, underscores, or dashes. The first character must be an alphabet.")

      val engineForm = Form(tuple(
        "engineinfoid" -> (text verifying ("This feature will be available soon.", e => supportedEngineTypes.contains(e))),
        "enginename" -> (text verifying ("Please name your engine.", enginename => enginename.length > 0)
          verifying enginenameConstraint)
      ) verifying ("Engine name must be unique.", f => !engines.existsByAppidAndName(appid, f._2))
        verifying ("Engine type is invalid.", f => engineInfos.get(f._1).map(_ => true).getOrElse(false)))

      engineForm.bindFromRequest.fold(
        formWithError => {
          val msg = formWithError.errors(0).message // extract 1st error message only
          BadRequest(toJson(Map("message" -> toJson(msg))))
        },
        formData => {
          val (enginetype, enginename) = formData
          val engineInfo = engineInfos.get(enginetype).get
          val engineId = engines.insert(Engine(
            id = -1,
            appid = appid,
            name = enginename,
            infoid = enginetype,
            itypes = None, // NOTE: default None (means all itypes)
            params = engineInfo.params.map(s => (s._2.id, s._2.defaultvalue))
          ))
          Logger.info("Create engine ID " + engineId)

          // automatically create default algo
          val defaultAlgoType = engineInfo.defaultalgoinfoid
          val params = algoInfos.get(defaultAlgoType).get.params.mapValues(_.defaultvalue)

          val defaultAlgo = Algo(
            id = -1,
            engineid = engineId,
            name = "Default-Algo",
            infoid = defaultAlgoType,
            command = "",
            params = params,
            settings = Map(), // no use for now
            modelset = false, // init value
            createtime = DateTime.now,
            updatetime = DateTime.now,
            status = "deployed", // this is default deployed algo
            offlineevalid = None,
            loop = None
          )

          val algoId = algos.insert(defaultAlgo)
          Logger.info("Create algo ID " + algoId)

          WS.url(settingsSchedulerUrl + "/users/" + user.id + "/sync").get()

          Ok(Json.obj(
            "id" -> engineId, // engine id
            "engineinfoid" -> enginetype,
            "appid" -> appid,
            "enginename" -> enginename))
        }
      )

  }

  /**
   * Removes an engine
   *
   * {{{
   * DELETE
   * JSON parameters:
   *   None
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If deleted:
   *   Ok
   *
   * }}}
   *
   * @param appid the App ID
   * @param engineid the engine ID
   */
  def removeEngine(appid: Int, engineid: Int) = WithEngine.async(appid, engineid) { (user, app, eng) =>
    implicit request =>
      // don't delete if there is any sim eval and offline tune pending, or deployed algorithm
      val pendingSimEvals = Helper.getSimEvalsByEngineid(eng.id).filter(x => Helper.isPendingSimEval(x)).toList
      val pendingOfflineTunes = offlineTunes.getByEngineid(eng.id).filter(x => Helper.isPendingOfflineTune(x)).toList
      val deployedAlgos = algos.getDeployedByEngineid(eng.id).toList

      if (deployedAlgos.size != 0) {
        val names = deployedAlgos map (x => x.name) mkString (",")
        Future.successful(Forbidden(Json.obj("message" -> s"This engine has deployed algorithms (${names}). Please undeploy them before delete this engine.")))
      } else if (pendingSimEvals.size != 0) {
        Future.successful(Forbidden(Json.obj("message" -> "There are running simulated evaluations. Please stop and delete them before delete this engine.")))
      } else if (pendingOfflineTunes.size != 0) {
        Future.successful(Forbidden(Json.obj("message" -> "There are auto-tuning algorithms. Please stop and delete them before delete this engine.")))
      } else {
        /** Deletion could take a while */
        val timeout = play.api.libs.concurrent.Promise.timeout("Scheduler is unreachable. Giving up.", concurrent.duration.Duration(10, concurrent.duration.MINUTES))

        // to scheduler: delete engine
        val delete = Helper.deleteEngineScheduler(appid, engineid)

        concurrent.Future.firstCompletedOf(Seq(delete, timeout)).map {
          case r: SimpleResult => {
            if (r.header.status == http.Status.OK) {
              Helper.deleteEngine(engineid, appid, keepSettings = false)
            }
            r
          }
          case t: String => InternalServerError(Json.obj("message" -> t))
        }
      }
  }

  /**
   * Returns a list of available (added but not deployed) algorithms of this engine
   * {{{
   * GET
   * JSON parameters:
   *   None
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If no algo:
   *   NoContent
   *
   *   If algos found:
   *   [ { see algoToJson
   *     }, ...
   *   ]
   * }}}
   * @note algo status
   *   TODO: add more info here...
   *
   * @param appid the App ID
   * @param engineid the engine ID
   */
  def getAvailableAlgoList(appid: Int, engineid: Int) = WithEngine(appid, engineid) { (user, app, eng) =>
    implicit request =>

      val engineAlgos = algos.getByEngineid(engineid).filter { Helper.isAvailableAlgo(_) }

      if (!engineAlgos.hasNext) NoContent
      else
        Ok(Json.toJson( // NOTE: only display algos which are not "deployed", nor "simeval"
          engineAlgos.map { algo =>
            val algoInfo = algoInfos.get(algo.infoid)
            algoToJson(algo, appid, algoInfo)
          }.toSeq
        ))
  }

  /**
   * Returns an available algorithm
   * {{{
   * GET
   * JSON parameters:
   *   None
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If algo not found:
   *   NotFound
   *   {
   *     "message" : "Invalid app id, engine id or algo id."
   *   }
   *
   *   If found:
   *   Ok
   *   {
   *     see algoToJson
   *   }
   * }}}
   *
   * @param appid the App ID
   * @param engineid the engine ID
   * @param id the algo ID
   */
  def getAvailableAlgo(appid: Int, engineid: Int, id: Int) = WithAlgo(appid, engineid, id) { (user, app, eng, algo) =>
    implicit request =>
      algoInfos.get(algo.infoid).map { info =>
        Ok(algoToJson(algo, appid, Some(info)))
      }.getOrElse {
        InternalServerError(Json.obj("message" -> s"Algoinfo ${algo.infoid} not found."))
      }
  }

  /**
   * Creates an new algorithm
   * {{{
   * POST
   * JSON parameters:
   *   {
   *     "algoinfoid" : <algo info id>,
   *     "algoname" : <algo name>,
   *   }
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If invalid appid or engineid:
   *   NotFound
   *   {
   *     "message" : <error message>
   *   }
   *
   *   If creation failed:
   *   BadRequest
   *   {
   *     "message" : <error message>
   *   }
   *
   *   If created:
   *   Ok
   *   {
   *     see algoToJson
   *   }
   *
   * }}}
   *
   * @param appid the App ID
   * @param engineid the engine ID
   */
  def createAvailableAlgo(appid: Int, engineid: Int) = WithEngine(appid, engineid) { (user, app, eng) =>
    implicit request =>

      val supportedAlgoTypes: Seq[String] = algoInfos.getAll map { _.id }
      val algonameConstraint = Constraints.pattern(nameRegex, "constraint.algoname", "Algorithm names should only contain alphanumerical characters, underscores, or dashes. The first character must be an alphabet.")

      val createAlgoForm = Form(tuple(
        "algoinfoid" -> (nonEmptyText verifying ("This feature will be available soon.", t => supportedAlgoTypes.contains(t))),
        "algoname" -> (text verifying ("Please name your algo.", name => name.length > 0)
          verifying algonameConstraint) // same name constraint as engine
      ) verifying ("Algo name must be unique.", f => !algos.existsByEngineidAndName(engineid, f._2)))

      createAlgoForm.bindFromRequest.fold(
        formWithError => {
          val msg = formWithError.errors(0).message // extract 1st error message only
          BadRequest(toJson(Map("message" -> toJson(msg))))
        },
        formData => {
          val (algoType, algoName) = formData

          algoInfos.get(algoType).map { algoInfo =>

            val newAlgo = Algo(
              id = -1,
              engineid = engineid,
              name = algoName,
              infoid = algoType,
              command = "",
              params = algoInfo.params.mapValues(_.defaultvalue),
              settings = Map(), // no use for now
              modelset = false, // init value
              createtime = DateTime.now,
              updatetime = DateTime.now,
              status = "ready", // default status
              offlineevalid = None,
              loop = None
            )

            val algoId = algos.insert(newAlgo)
            Logger.info("Create algo ID " + algoId)

            Ok(algoToJson(newAlgo.copy(id = algoId), appid, Some(algoInfo)))
          }.getOrElse {
            InternalServerError(Json.obj("message" -> s"Algoinfo ${algoType} not found."))
          }
        }
      )
  }

  /**
   * Deletes an algorithm
   * {{{
   * DELETE
   * JSON parameters:
   *   None
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If deleted:
   *   Ok
   * }}}
   *
   * @param appid the App ID
   * @param engineid the engine ID
   * @param id the algo ID
   */
  def removeAvailableAlgo(appid: Int, engineid: Int, id: Int) = WithAlgo.async(appid, engineid, id) { (user, app, eng, algo) =>
    implicit request =>
      /** Deletion could take a while */
      val timeout = play.api.libs.concurrent.Promise.timeout("Scheduler is unreachable. Giving up.", concurrent.duration.Duration(10, concurrent.duration.MINUTES))

      val deleteTune = algo.offlinetuneid map { tuneid =>
        offlineTunes.get(tuneid) map { tune =>
          /** Make sure to unset offline tune's creation time to prevent scheduler from picking up */
          offlineTunes.update(tune.copy(createtime = None))

          Helper.stopAndDeleteOfflineTuneScheduler(appid, engineid, tuneid)
        } getOrElse {
          concurrent.Future { Ok }
        }
      } getOrElse {
        concurrent.Future { Ok }
      }

      val deleteAlgo: concurrent.Future[SimpleResult] = Helper.deleteAlgoScheduler(appid, engineid, algo.id)

      val complete: concurrent.Future[SimpleResult] = concurrent.Future.reduce(Seq(deleteTune, deleteAlgo)) { (a, b) =>
        if (a.header.status != http.Status.OK) // keep the 1st error
          a
        else
          b
      }

      concurrent.Future.firstCompletedOf(Seq(complete, timeout)).map {
        case r: SimpleResult => {
          if (r.header.status == http.Status.OK) {
            algo.offlinetuneid map { tuneid =>
              Helper.deleteOfflineTune(tuneid, keepSettings = false)
            }
            Helper.deleteAlgo(algo.id, keepSettings = false)
          }
          r
        }
        case t: String => InternalServerError(Json.obj("message" -> t))
      }
  }

  /**
   * Returns a list of deployed algorithms
   * {{{
   * GET
   * JSON parameters:
   *   None
   * JSON repseon:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If no deployed algo:
   *   NoContent
   *
   *   If deployed algos found:
   *   {
   *     "updatedtime" : <TODO>,
   *     "status" : <TODO>,
   *     "algolist" : [ { see algoToJson
   *                    }, ...
   *                  ]
   *   }
   * }}}
   *
   * @param appid the App ID
   * @param engineid the engine ID
   */
  def getDeployedAlgoList(appid: Int, engineid: Int) = WithEngine(appid, engineid) { (user, app, eng) =>
    implicit request =>

      val deployedAlgos = algos.getDeployedByEngineid(engineid)

      if (!deployedAlgos.hasNext) NoContent
      else
        Ok(Json.obj(
          "updatedtime" -> "12-03-2012 12:32:12", // TODO: what's this time for?
          "status" -> "Running",
          "algolist" -> Json.toJson(deployedAlgos.map { algo =>
            val algoInfo = algoInfos.get(algo.infoid)
            algoToJson(algo, appid, algoInfo)
          }.toSeq)
        ))
  }

  /**
   * Deploys a list of algorithms (also undeploys any existing deployed algorithms)
   * The status of deployed algorithms change to "deployed"
   *
   * {{{
   * POST
   * JSON parameters:
   *   {
   *     "algoidlist" : [ array of algo ids ]
   *   }
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If any of the algo id is not valid:
   *   BadRequest
   *   {
   *     "message" : <error message>
   *   }
   *
   *   If done:
   *   Ok
   *
   * }}}
   *
   * @param appid the App ID
   * @param engineid the engine ID
   */
  def algoDeploy(appid: Int, engineid: Int) = WithEngine(appid, engineid) { (user, app, eng) =>
    implicit request =>
      val deployForm = Form(
        "algoidlist" -> list(number)
      )
      deployForm.bindFromRequest.fold(
        formWithErrors => Ok,
        form => {
          val algoidList = form
          val algoList = algoidList.map(id => (id, algos.getByIdAndEngineid(id, engineid)))
          val invalidAlgos = algoList.filter {
            case (id, algoOpt) => algoOpt match {
              case None => true // not exist
              case Some(x) => (x.status != "ready") // not ready
            }
          }

          // make sure all algoids are valid
          if (!invalidAlgos.isEmpty) {
            val ids = invalidAlgos.map { case (id, algoOpt) => id }.mkString(", ")
            BadRequest(Json.obj("message" -> s"Invalid algo ids: ${ids}."))
          } else {
            algos.getDeployedByEngineid(engineid).foreach { algo =>
              algos.update(algo.copy(status = "ready"))
            }
            algoList.foreach {
              case (id, algoOpt) =>
                // algoOpt can't be None because of invalidAlgos check
                algos.update(algoOpt.get.copy(status = "deployed"))
            }
            WS.url(settingsSchedulerUrl + "/users/" + user.id + "/sync").get()
            Ok
          }
        }
      )
  }

  /**
   * Undeploys all deployed algorithms
   * {{{
   * POST
   * JSON parameters:
   *   None
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If done:
   *   Ok
   * }}}
   *
   * @param appid the App ID
   * @param engineid the engine ID
   */
  def algoUndeploy(appid: Int, engineid: Int) = WithEngine(appid, engineid) { (user, app, eng) =>
    implicit request =>

      algos.getDeployedByEngineid(engineid) foreach { algo =>
        algos.update(algo.copy(status = "ready"))
      }
      WS.url(settingsSchedulerUrl + "/users/" + user.id + "/sync").get()
      Ok
  }

  /**
   * Requests to train model now
   * {{{
   * POST
   * JSON parameters:
   *   None
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If error:
   *   InternalServerError
   *   {
   *     "message" : <error message>
   *   }
   *
   *   If done:
   *   Ok
   *   {
   *     "message" : <message from scheduler>
   *   }
   * }}}
   *
   * @param appid the App ID
   * @param engineid the engine ID
   */
  def algoTrainNow(appid: Int, engineid: Int) = WithEngine.async(appid, engineid) { (user, app, eng) =>
    implicit request =>
      // No extra param required
      val timeout = play.api.libs.concurrent.Promise.timeout("Scheduler is unreachable. Giving up.", concurrent.duration.Duration(10, concurrent.duration.MINUTES))
      val request = WS.url(s"${settingsSchedulerUrl}/apps/${appid}/engines/${engineid}/trainoncenow").get() map { r =>
        Ok(Json.obj("message" -> (r.json \ "message").as[String]))
      } recover {
        case e: Exception => InternalServerError(Json.obj("message" -> e.getMessage()))
      }

      /** Detect timeout (10 minutes by default) */
      concurrent.Future.firstCompletedOf(Seq(request, timeout)).map {
        case r: SimpleResult => r
        case t: String => InternalServerError(Json.obj("message" -> t))
      }
  }

  /**
   * Returns a list of simulated evalulation for this engine
   *
   * {{{
   * GET
   * JSON parameters:
   *   None
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If no sim evals:
   *   NoContent
   *
   *   If found:
   *   Ok
   *   [
   *     { "id" : <sim eval id>,
   *       "appid" : <app id>,
   *       "engineid" : <engine id>,
   *       "algolist" : [
   *                      { "id" : <algo id>,
   *                        "algoname" : <algo name>,
   *                        "appid" : <app id>,
   *                        "engineid" : <engine id>,
   *                        "algoinfoid" : <algo info id>,
   *                        "algoinfoname" : <algo info name>,
   *                        "settingsstring" : <algo setting string>
   *                      }, ...
   *                    ],
   *       "status" : <sim eval status>,
   *       "createtime" : <sim eval create time>,
   *       "endtime" : <sim eval end time>
   *     }, ...
   *   ]
   * }}}
   *
   * @param appid the App ID
   * @param engineid the engine ID
   */
  def getSimEvalList(appid: Int, engineid: Int) = WithEngine(appid, engineid) { (user, app, eng) =>
    implicit request =>

      // get offlineeval for this engine
      val engineOfflineEvals = Helper.getSimEvalsByEngineid(engineid)

      if (!engineOfflineEvals.hasNext) NoContent
      else {
        val resp = Json.toJson(

          engineOfflineEvals.map { eval =>

            val algolist = Json.toJson(
              algos.getByOfflineEvalid(eval.id).map { algo =>
                val algoInfo = algoInfos.get(algo.infoid)
                algoToJson(algo, appid, algoInfo, withParam = true)
              }.toSeq
            )

            val createtime = eval.createtime.map(dateTimeToString(_)).getOrElse("-")
            val starttime = eval.starttime.map(dateTimeToString(_)).getOrElse("-")
            val endtime = eval.endtime.map(dateTimeToString(_)).getOrElse("-")

            Json.obj(
              "id" -> eval.id,
              "appid" -> appid,
              "engineid" -> eval.engineid,
              "algolist" -> algolist,
              "status" -> getSimEvalStatus(eval),
              "createtime" -> createtime, // NOTE: use createtime here for test date
              "endtime" -> endtime
            )
          }.toSeq
        )
        Ok(resp)
      }
  }

  /**
   * Creates a simulated evalution
   *
   * {{{
   * POST
   * JSON parameters:
   *
   *  {
   *    "infoid[i]": <metric or splitter info id>
   *    "infotype[i]": <"offlineevalmetric" or "offlineevalsplitter">
   *    "other param [i]": <the parameter value for corresponding infoid[i]>
   *    "algo" : <list of algo ids to be evaluated>
   *    "splittrain": <training set split percentage 1 to 100>,
   *    "splittest": <test set split percentage 1 to 100>,
   *    "evaliteration": <number of iterations>
   *  }
   *
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If error:
   *   BadRequest
   *   {
   *     "message" : <error message>
   *   }
   *
   *   If created:
   *   Ok
   *
   * }}}
   * @param appid the App ID
   * @param engineid the engine ID
   */
  def createSimEval(appid: Int, engineid: Int) = WithEngine(appid, engineid) { (user, app, eng) =>
    implicit request =>
      val simEvalForm = Form(tuple(
        "infoid" -> seqOfMapOfStringToAny,
        "algoids" -> list(number), // algo id
        "splittrain" -> number(1, 100),
        "splittest" -> number(1, 100),
        "evaliteration" -> (number verifying ("Number of Iteration must be greater than 0", x => (x > 0)))
      ))

      simEvalForm.bindFromRequest.fold(
        e => BadRequest(Json.obj("message" -> e.toString)),
        f => {
          val (params, algoids, splitTrain, splitTest, evalIteration) = f

          val metricList = params.filter(p => (p("infotype") == "offlineevalmetric")).map(p =>
            OfflineEvalMetric(
              id = 0,
              infoid = p("infoid").asInstanceOf[String],
              evalid = 0, // will be assigned later
              params = p - "infoid" - "infotype" // remove these keys from params
            )).toList

          // percentage param is standard for all splitter
          val percentageParam = Map(
            "trainingPercent" -> (splitTrain.toDouble / 100),
            "validationPercent" -> 0.0, // no validatoin set for sim eval
            "testPercent" -> (splitTest.toDouble / 100)
          )

          // get list of algo obj
          val optAlgos: List[Option[Algo]] = algoids.map { algos.get(_) }

          // TODO: Allow selection of splitter
          // Use Hadoop version if any algo in the list requires Hadoop
          val hadoopRequired = optAlgos.map { optAlgo =>
            optAlgo map { algo =>
              algoInfos.get(algo.infoid) map { info =>
                info.techreq.contains("Hadoop")
              } getOrElse false
            } getOrElse false
          } reduce { _ || _ }

          val splitterList = params.filter(p => (p("infotype") == "offlineevalsplitter")).map(p =>
            OfflineEvalSplitter(
              id = 0,
              evalid = 0, // will be assigned later
              name = "", // will be assigned later
              //infoid = p("infoid").asInstanceOf[String],
              infoid = if (hadoopRequired) "trainingtestsplit" else "u2isplit",
              settings = p ++ percentageParam - "infoid" - "infotype" // remove these keys from params
            )).toList

          if (metricList.length == 0)
            BadRequest(Json.obj("message" -> "At least one metric is required."))
          else if (splitterList.length == 0)
            BadRequest(Json.obj("message" -> "One Splitter is required."))
          else if (splitterList.length > 1)
            BadRequest(Json.obj("message" -> "Multiple Splitters are not supported now."))
          else if (optAlgos.contains(None))
            BadRequest(Json.obj("message" -> "Invalid algo ids."))
          else {
            val algoList: List[Algo] = optAlgos.map(_.get) // NOTE: already check optAlgos does not contain None

            val evalid = createOfflineEval(eng, algoList, metricList, splitterList(0), evalIteration)

            WS.url(settingsSchedulerUrl + "/users/" + user.id + "/sync").get()

            Ok
          }
        }
      )
  }

  /**
   * Requests to stop and delete simulated evalution (including running/pending job)
   *
   * {{{
   * DELETE
   * JSON parameters:
   *   None
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If not found:
   *   NotFound
   *   {
   *     "message" : <error message>
   *   }
   *
   *   If error:
   *   InternalServerError
   *   {
   *     "message" : <error message>
   *   }
   *
   *   If deleted:
   *   Ok
   *   {
   *      "message" : "Offline evaluation ID $id has been deleted"
   *   }
   * }}}
   *
   * @param appid the App ID
   * @param engineid the engine ID
   * @param id the offline evaluation ID
   *
   */
  def removeSimEval(appid: Int, engineid: Int, id: Int) = WithOfflineEval.async(appid, engineid, id) { (user, app, eng, oe) =>
    implicit request =>
      // remove algo, remove metric, remove offline eval
      /** Make sure to unset offline eval's creation time to prevent scheduler from picking up */
      offlineEvals.update(oe.copy(createtime = None))

      /** Deletion of app data and model data could take a while */
      val timeout = play.api.libs.concurrent.Promise.timeout("Scheduler is unreachable. Giving up.", concurrent.duration.Duration(10, concurrent.duration.MINUTES))

      val complete = Helper.stopAndDeleteSimEvalScheduler(appid, engineid, oe.id)

      /** Detect timeout (10 minutes by default) */
      concurrent.Future.firstCompletedOf(Seq(complete, timeout)).map {
        case r: SimpleResult => {
          if (r.header.status == http.Status.OK) {
            Helper.deleteOfflineEval(oe.id, keepSettings = false)
          }
          r
        }
        case t: String => InternalServerError(Json.obj("message" -> t))
      }
  }

  /**
   * Returns the simulated evaluation report
   *
   * {{{
   * GET
   * JSON parameters:
   *   None
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If not found:
   *   {
   *     "message" : <error message>
   *   }
   *
   *   If found:
   *   Ok
   *   {
   *     "id" : <sim eval id>,
   *     "appid" : <app id>,
   *     "engineid" : <engine id>,
   *     "algolist" : [
   *                    {
   *                      "id" : <algo id>,
   *                      "algoname" : <algo name>,
   *                      "appid" : <app id>,
   *                      "engineid" : <engine id>,
   *                      "algoinfoid" : <algo info id>,
   *                      "algoinfoname" : <algo info name>,
   *                      "settingsstring" : <algo setting string>
   *                    }, ...
   *                  ],
   *     "metricslist" : [
   *                       {
   *                         "id" : <metric id>,
   *                         "metricsinfoid" : <metric info id>,
   *                         "metricsname" : <metric info name>,
   *                         "settingsstring" : <metric setting string>
   *                       }, ...
   *                     ],
   *     "metricscorelist" : [
   *                           {
   *                             "algoid" : <algo id>,
   *                             "metricsid" : <metric id>,
   *                             "score" : <average score>
   *                           }, ...
   *                         ],
   *     "metricscoreiterationlist" : [
   *                                    [{
   *                                      "algoid" : <algo id>,
   *                                      "metricsid" : <metric id>,
   *                                      "score" : <score of 1st iteration>
   *                                     }, ...
   *                                    ],
   *                                    [{
   *                                      "algoid" : <algo id>,
   *                                      "metricsid" : <metric id>,
   *                                      "score" : <score of 2nd iteration>
   *                                     }, ...
   *                                    ],
   *                                  ],
   *     "splittrain" : <training set split percentage 1 to 100>,
   *     "splittest" : <test set split percentage 1 to 100>,
   *     "splittersettingsstring" : <splitter setting string>,
   *     "evaliteration" : <number of iterations>,
   *     "status" : <eval status>,
   *     "starttime" : <start time>,
   *     "endtime" : <end time>
   *   }
   * }}}
   *
   * @param appid the App ID
   * @param engineid the engine ID
   * @param id the offline evaluation ID
   */
  def getSimEvalReport(appid: Int, engineid: Int, id: Int) = WithOfflineEval(appid, engineid, id) { (user, app, eng, eval) =>
    implicit request =>

      val status = getSimEvalStatus(eval)

      val evalAlgos = algos.getByOfflineEvalid(eval.id).toArray

      val algolist = Json.toJson(
        evalAlgos.map { algo =>
          val algoInfo = algoInfos.get(algo.infoid)
          algoToJson(algo, appid, algoInfo, withParam = true)
        }.toSeq
      )

      val evalMetrics = offlineEvalMetrics.getByEvalid(eval.id).toArray

      val metricslist = Json.toJson(
        evalMetrics.map { metric =>
          val metricInfo = offlineEvalMetricInfos.get(metric.infoid)
          offlineEvalMetricToJson(metric, metricInfo, withParam = true)
        }.toSeq
      )

      val evalResults = offlineEvalResults.getByEvalid(eval.id).toArray

      val metricscorelist = Json.toJson(
        (for (algo <- evalAlgos; metric <- evalMetrics) yield {

          val results = evalResults
            .filter(x => ((x.metricid == metric.id) && (x.algoid == algo.id)))
            .map(x => x.score)

          val num = results.length
          val avg = if ((results.isEmpty) || (num != eval.iterations)) "N/A" else ((results.reduceLeft(_ + _) / num).toString)

          Json.obj(
            "algoid" -> algo.id,
            "metricsid" -> metric.id,
            "score" -> avg
          )
        }).toSeq
      )

      val metricscoreiterationlist = Json.toJson((for (i <- 1 to eval.iterations) yield {
        val defaultScore = (for (algo <- evalAlgos; metric <- evalMetrics) yield {
          ((algo.id, metric.id) -> "N/A") // default score
        }).toMap

        val evalScore = evalResults.filter(x => (x.iteration == i)).map(r =>
          ((r.algoid, r.metricid) -> r.score.toString)).toMap

        // overwrite defaultScore with evalScore
        (defaultScore ++ evalScore).map {
          case ((algoid, metricid), score) =>
            Json.obj("algoid" -> algoid, "metricsid" -> metricid, "score" -> score)
        }
      }).toSeq)

      val starttime = eval.starttime map (dateTimeToString(_)) getOrElse ("-")
      val endtime = eval.endtime map (dateTimeToString(_)) getOrElse ("-")

      // get splitter data
      val splitters = offlineEvalSplitters.getByEvalid(eval.id)

      if (splitters.hasNext) {
        val splitter = splitters.next

        val splitTrain = ((splitter.settings("trainingPercent").asInstanceOf[Double]) * 100).toInt
        val splitTest = ((splitter.settings("testPercent").asInstanceOf[Double]) * 100).toInt

        if (splitters.hasNext) {
          InternalServerError(Json.obj("message" -> s"More than one splitter found for this Offline Eval ID: ${eval.id}"))
        } else {
          Ok(Json.obj(
            "id" -> eval.id,
            "appid" -> appid,
            "engineid" -> eval.engineid,
            "algolist" -> algolist,
            "metricslist" -> metricslist,
            "metricscorelist" -> metricscorelist,
            "metricscoreiterationlist" -> metricscoreiterationlist,
            "splittrain" -> splitTrain,
            "splittest" -> splitTest,
            "splittersettingsstring" -> offlineEvalSplitterParamToString(splitter, offlineEvalSplitterInfos.get(splitter.infoid)),
            "evaliteration" -> eval.iterations,
            "status" -> status,
            "starttime" -> starttime,
            "endtime" -> endtime
          ))
        }
      } else {
        InternalServerError(Json.obj("message" -> s"No splitter found fo this Offline Eval ID ${eval.id}."))
      }

  }

  /**
   * Returns the algorithm auto tune report
   *
   * {{{
   * GET
   * JSON parameters:
   *   None
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If not found:
   *   NotFound
   *   {
   *     "message" : <error message>
   *   }
   *
   *   If found:
   *   Ok
   *   {
   *     "id" : <original algo id>,
   *     "appid" : <app id>,
   *     "engineid" : <engine id>,
   *     "algo" : {
   *                "id" : <original algo id>,
   *                "algoname" : <algo name>,
   *                "appid" : <app id>,
   *                "engineid" : <engine id>,
   *                "algoinfoid" : <algo info id>,
   *                "algoinfoname" : <algo info name>,
   *                "settingsstring" : <algo setting string>
   *              },
   *     "metric" : {
   *                  "id" : <metric id>,
   *                  "metricsinfoid" : <metric info id>,
   *                  "metricsname" : <metric info name>,
   *                  "settingsstring" : <metric setting string>
   *                },
   *     "metricscorelist" : [
   *                           {
   *                             "algoautotuneid" : <tuned algo id>,
   *                             "algoinfoname" : <algo info name>,
   *                             "settingsstring" : <algo setting string>,
   *                             "score" : <average score>
   *                           }, ...
   *                         ],
   *     "metricscoreiterationlist" : [
   *                                    [ {
   *                                        "algoautotuneid" : <tuned algo id 1>,
   *                                        "algoinfoname" : <algo info name>,
   *                                        "settingsstring" : <algo setting string>,
   *                                        "score" : <score of 1st iteration for this tuned algo id>
   *                                      },
   *                                      {
   *                                        "algoautotuneid" : <tuned algo id 2>,
   *                                        "algoinfoname" : <algo info name>,
   *                                        "settingsstring" : <algo setting string>,
   *                                        "score" : <score of 1st iteration for this tuned algo id>
   *                                      }, ...
   *                                    ],
   *                                    [ {
   *                                        "algoautotuneid" : <tuned algo id 1>,
   *                                        "algoinfoname" : <algo info name>,
   *                                        "settingsstring" : <algo setting string>,
   *                                        "score" : <score of 2nd iteration for this tuned algo id>
   *                                      },
   *                                      {
   *                                        "algoautotuneid" : <tuned algo id 2>,
   *                                        "algoinfoname" : <algo info name>,
   *                                        "settingsstring" : <algo setting string>,
   *                                        "score" : <score of 2nd iteration for this tuned algo id>
   *                                      }, ...
   *                                    ], ...
   *                                  ],
   *     "splittrain" : <training set split percentage 1 to 100>,
   *     "splitvalidation" : <validation set split percentage 1 to 100>,
   *     "splittest" : <test set split percentage 1 to 100>,
   *     "splitmethod" : <split method string: randome, time>
   *     "splittersettingsstring" : <splitter setting string>,
   *     "evaliteration" : <number of iterations>,
   *     "status" : <auto tune status>,
   *     "starttime" : <start time>,
   *     "endtime" : <end time>
   *   }
   * }}}
   *
   * @param appid the App ID
   * @param engineid the engine ID
   * @param algoid the algo ID
   */
  def getAlgoAutotuningReport(appid: Int, engineid: Int, algoid: Int) = WithAlgo(appid, engineid, algoid) { (user, app, eng, algo) =>
    implicit request =>

      algo.offlinetuneid map { tuneid =>
        offlineTunes.get(tuneid) map { tune =>

          val algoInfo = algoInfos.get(algo.infoid)

          // get all offlineeval of this offlinetuneid
          val tuneOfflineEvals: Array[OfflineEval] = offlineEvals.getByTuneid(tuneid).toArray.sortBy(_.id)

          val tuneMetrics: Array[OfflineEvalMetric] = tuneOfflineEvals.flatMap { e => offlineEvalMetrics.getByEvalid(e.id) }

          val tuneSplitters: Array[OfflineEvalSplitter] = tuneOfflineEvals.flatMap { e => offlineEvalSplitters.getByEvalid(e.id) }

          // get all offlineeavlresults of each offlineevalid
          val tuneOfflineEvalResults: Array[OfflineEvalResult] = tuneOfflineEvals.flatMap { e => offlineEvalResults.getByEvalid(e.id) }

          // test set score
          val tuneOfflineEvalResultsTestSet: Array[OfflineEvalResult] = tuneOfflineEvalResults.filter(x => (x.splitset == "test"))

          val tuneOfflineEvalResultsTestSetAlgoidMap: Map[Int, Double] = tuneOfflineEvalResultsTestSet.map(x => (x.algoid -> x.score)).toMap

          // get all algos of each offlineevalid
          // note: this happen after getting all available offlineEvalResults,
          // so these retrieved algos may have more algo than those used in offlineEvalResults.
          val tuneAlgos: Array[Algo] = tuneOfflineEvals flatMap { e => algos.getByOfflineEvalid(e.id) }

          val tuneAlgosMap: Map[Int, Algo] = tuneAlgos.map { a => (a.id -> a) }.toMap

          // group by (loop, paramset)
          type AlgoGroupIndex = (Option[Int], Option[Int])

          val tuneAlgosGroup: Map[AlgoGroupIndex, Array[Algo]] = tuneAlgos.groupBy(a => (a.loop, a.paramset))

          // get param of each group
          val tuneAlgosGroupParams: Map[AlgoGroupIndex, (Int, String, String)] = tuneAlgosGroup.map {
            case (index, arrayOfAlgos) =>
              val tAlgo = arrayOfAlgos(0) // just take 1, all algos of this group will have same params
              val tAlgoInfo: Option[AlgoInfo] = algoInfos.get(tAlgo.infoid)
              val infoName = tAlgoInfo.map(_.name).getOrElse("Algo info ${tAlgo.infoid} not found")
              val settings = algoParamToString(tAlgo, tAlgoInfo)

              (index -> (tAlgo.id, settings, infoName))
          }

          // calculate avg
          val avgScores: Map[AlgoGroupIndex, String] = tuneAlgosGroup.mapValues { arrayOfAlgos =>
            // check if all scores available
            val allAvailable = arrayOfAlgos.map(algo => tuneOfflineEvalResultsTestSetAlgoidMap.contains(algo.id)).reduceLeft(_ && _)

            if (allAvailable) {
              val scores: Array[Double] = arrayOfAlgos.map(algo => tuneOfflineEvalResultsTestSetAlgoidMap(algo.id))

              (scores.sum / scores.size).toString
            } else {
              "N/A"
            }
          }

          val metricscorelist = avgScores.toSeq.sortBy(_._1).map {
            case (k, v) =>
              Json.obj(
                "algoautotuneid" -> tuneAlgosGroupParams(k)._1,
                "algoinfoname" -> tuneAlgosGroupParams(k)._3,
                "settingsstring" -> tuneAlgosGroupParams(k)._2,
                "score" -> v)
          }

          val metricscoreiterationlist = tuneOfflineEvals.map { e =>
            tuneOfflineEvalResultsTestSet.filter(x => (x.evalid == e.id)).sortBy(_.algoid).map { r =>

              val tAlgo = tuneAlgosMap(r.algoid)
              val tAlgoInfo: Option[AlgoInfo] = algoInfos.get(tAlgo.infoid)
              val infoName = tAlgoInfo.map(_.name).getOrElse("Algo info ${tAlgo.infoid} not found")

              Json.obj(
                "algoautotuneid" -> r.algoid,
                "algoinfoname" -> infoName,
                "settingsstring" -> algoParamToString(tAlgo, tAlgoInfo),
                "score" -> r.score)
            }.toSeq
          }.toSeq

          val metric = tuneMetrics(0)
          val metricInfo = offlineEvalMetricInfos.get(metric.infoid)
          val splitter = tuneSplitters(0)
          val splitTrain = ((splitter.settings("trainingPercent").asInstanceOf[Double]) * 100).toInt
          val splitValidation = ((splitter.settings("validationPercent").asInstanceOf[Double]) * 100).toInt
          val splitTest = ((splitter.settings("testPercent").asInstanceOf[Double]) * 100).toInt
          val evalIteration = tuneOfflineEvals.size // NOTE: for autotune, number of offline eval is the iteration
          val engineinfoid = engines.get(engineid) map { _.infoid } getOrElse { "unkown-engine" }

          val starttime = tune.starttime map (dateTimeToString(_)) getOrElse ("-")
          val endtime = tune.endtime map (dateTimeToString(_)) getOrElse ("-")

          Ok(Json.obj(
            "id" -> algoid,
            "appid" -> appid,
            "engineid" -> engineid,
            "algo" -> algoToJson(algo, appid, algoInfo, withParam = true),
            "metric" -> offlineEvalMetricToJson(metric, metricInfo, withParam = true),
            "metricscorelist" -> Json.toJson(metricscorelist),
            "metricscoreiterationlist" -> Json.toJson(metricscoreiterationlist),
            "splittrain" -> splitTrain,
            "splitvalidation" -> splitValidation,
            "splittest" -> splitTest,
            "splittersettingsstring" -> offlineEvalSplitterParamToString(splitter, offlineEvalSplitterInfos.get(splitter.infoid)),
            "evaliteration" -> evalIteration,
            "status" -> getOfflineTuneStatus(tune),
            "starttime" -> starttime,
            "endtime" -> endtime
          ))
        } getOrElse {
          InternalServerError(Json.obj("message" -> s"The offline tune id ${tuneid} does not exist."))
        }
      } getOrElse {
        InternalServerError(Json.obj("message" -> "This algo does not have offline tune."))
      }
  }

  /**
   * Applies the selected tuned algo's params to this algo
   *
   * {{{
   * POST
   * JSON parameters:
   *   {
   *     "tunedalgoid" : <the tuned algo id. This algo's parameters will be used>
   *   }
   * JSON response:
   *   If not authenticated:
   *   Forbidden
   *   {
   *     "message" : "Haven't signed in yet."
   *   }
   *
   *   If error:
   *   BadRequest
   *   {
   *     "message" : <error message>
   *   }
   *
   *   If algo not found:
   *   NotFound
   *   {
   *     "message" : <error message>
   *   }
   *   If applied:
   *   Ok
   *
   * }}}
   *
   * @param appid the App ID
   * @param engineid the engine ID
   * @param algoid the algo ID to which the tuned parameters are applied
   */
  def algoAutotuningSelect(appid: Int, engineid: Int, algoid: Int) = WithAlgo(appid, engineid, algoid) { (user, app, eng, algo) =>
    implicit request =>

      // Apply POST request params of tunedalgoid to algoid
      // update the status of algoid from 'tuning' or 'tuned' to 'ready'

      val form = Form("tunedalgoid" -> number)

      form.bindFromRequest.fold(
        formWithError => {
          val msg = formWithError.errors(0).message // extract 1st error message only
          BadRequest(toJson(Map("message" -> toJson(msg))))
        },
        formData => {
          val tunedAlgoid = formData

          val orgAlgo = algos.get(algoid)
          val tunedAlgo = algos.get(tunedAlgoid)

          if ((orgAlgo == None) || (tunedAlgo == None)) {
            NotFound(toJson(Map("message" -> toJson("Invalid app id, engine id or algo id."))))
          } else {
            val tunedAlgoParams = tunedAlgo.get.params ++ Map("tune" -> "manual")
            algos.update(orgAlgo.get.copy(
              params = tunedAlgoParams,
              status = "ready"
            ))

            Ok
          }
        }
      )
  }

  def getEngineSettings(appid: Int, engineid: Int) = WithEngine(appid, engineid) { (user, app, engine) =>
    implicit request =>
      engineInfos.get(engine.infoid) map { engineInfo =>
        val params = engineInfo.params.mapValues(_.defaultvalue) ++ engine.params

        Ok(toJson(Map(
          "id" -> toJson(engine.id), // engine id
          "appid" -> toJson(engine.appid),
          "allitemtypes" -> toJson(engine.itypes == None),
          "itemtypelist" -> engine.itypes.map(x => toJson(x.toIterator.toSeq)).getOrElse(JsNull),
          "trainingdisabled" -> engine.trainingdisabled.map(toJson(_)).getOrElse(toJson(false)),
          "trainingschedule" -> engine.trainingschedule.map(toJson(_)).getOrElse(toJson("0 0 * * * ?"))) ++
          (params map { case (k, v) => (k, toJson(v.toString)) })))
      } getOrElse {
        NotFound(toJson(Map("message" -> toJson(s"Invalid EngineInfo ID: ${engine.infoid}"))))
      }
  }

  def updateEngineSettings(appid: Int, engineid: Int) = WithEngine(appid, engineid) { (user, app, engine) =>
    implicit request =>
      val f = Form(tuple(
        "infoid" -> mapOfStringToAny,
        "allitemtypes" -> boolean,
        "itemtypelist" -> list(text),
        "trainingdisabled" -> boolean,
        "trainingschedule" -> text))
      f.bindFromRequest.fold(
        e => BadRequest(toJson(Map("message" -> toJson(e.toString)))),
        f => {
          val (params, allitemtypes, itemtypelist, trainingdisabled, trainingschedule) = f
          // NOTE: read-modify-write the original param
          val itypes = if (itemtypelist.isEmpty) None else Option(itemtypelist)
          val updatedParams = engine.params ++ params - "infoid"
          val updatedEngine = engine.copy(itypes = itypes, params = updatedParams, trainingdisabled = Some(trainingdisabled), trainingschedule = Some(trainingschedule))
          engines.update(updatedEngine)
          WS.url(settingsSchedulerUrl + "/users/" + user.id + "/sync").get()
          Ok
        })
  }

  def getEngineTemplateHtml(appid: Int, engineid: Int) = WithEngine(appid, engineid) { (user, app, engine) =>
    implicit request =>
      //Ok(views.html.engines.template(engine.infoid))
      engineInfos.get(engine.infoid) map { engineInfo =>
        if (engineInfo.paramsections.isEmpty)
          Ok(views.html.engines.template(handleParamSections[EngineInfo](Seq(ParamSection(
            name = "Parameter Settings",
            description = Some("No extra setting is required for this engine."))), engineInfo, 1), true))
        else
          Ok(views.html.engines.template(handleParamSections[EngineInfo](engineInfo.paramsections, engineInfo, 1), false))
      } getOrElse {
        NotFound(s"EngineInfo ID ${engine.infoid} not found")
      }
  }

  def getEngineTemplateJs(appid: Int, engineid: Int) = WithEngine(appid, engineid) { (user, app, engine) =>
    implicit request =>
      Ok(views.js.engines.template(engine.infoid, Seq()))
      engineInfos.get(engine.infoid) map { engineInfo =>
        Ok(views.js.engines.template(
          engineInfo.id,
          engineInfo.paramsections.map(collectParams(engineInfo, _)).foldLeft(Set[Param]())(_ ++ _).toSeq))
      } getOrElse {
        NotFound(s"EngineInfo ID ${engine.infoid} not found")
      }
  }

  def getAlgoSettings(appid: Int, engineid: Int, algoid: Int) = WithAlgo(appid, engineid, algoid) { (user, app, engine, algo) =>
    implicit request =>
      algoInfos.get(algo.infoid) map { algoInfo =>
        // get default params from algoinfo and combined with existing params
        val params = algoInfo.params.mapValues(_.defaultvalue) ++ algo.params

        Ok(toJson(Map(
          "id" -> toJson(algo.id),
          "appid" -> toJson(appid),
          "engineid" -> toJson(engineid)) ++
          (params map { case (k, v) => (k, toJson(v.toString)) })))
      } getOrElse {
        NotFound(toJson(Map("message" -> toJson(s"Invalid AlgoInfo ID: ${algo.infoid}"))))
      }
  }

  def updateAlgoSettings(appid: Int, engineid: Int, algoid: Int) = WithAlgo(appid, engineid, algoid) { (user, app, engine, algo) =>
    implicit request =>
      val f = Form(tuple("infoid" -> mapOfStringToAny, "tune" -> optional(text), "tuneMethod" -> optional(text)))
      f.bindFromRequest.fold(
        e => BadRequest(toJson(Map("message" -> toJson(e.toString)))),
        bound => {
          val (params, tune, tuneMethod) = bound
          // NOTE: read-modify-write the original param
          val tuneObj = (tune, tuneMethod) match {
            case (Some("manual"), _) => Map("tune" -> "manual")
            case (Some("auto"), Some(m)) => Map("tune" -> "auto", "tuneMethod" -> m)
            case (_, _) => Map()
          }
          val updatedParams = algo.params ++ params ++ tuneObj - "infoid"
          val updatedAlgo = algo.copy(params = updatedParams)

          if (updatedParams("tune") != "auto") {
            algos.update(updatedAlgo)
            Ok
          } else {
            // create offline eval with baseline algo
            // TODO: get from UI
            val defaultBaseLineAlgoType = engine.infoid match {
              case "itemrec" => "pdio-local-itemrec-random"
              case "itemsim" => "pdio-local-itemsim-random"
            }

            engineInfos.get(engine.infoid).map { engineInfo =>
              val hadoopRequired = algoInfos.get(algo.infoid) map { _.techreq.contains("Hadoop") } getOrElse false
              val metricinfoid = if (hadoopRequired) engineInfo.defaultofflineevalmetricinfoid else engineInfo.defaultofflineevalmetricinfoid + "_nd" // TODO: from UI
              val splitterinfoid = if (hadoopRequired) engineInfo.defaultofflineevalsplitterinfoid else "u2isplit" // TODO: from UI
              algoInfos.get(defaultBaseLineAlgoType).map { baseLineAlgoInfo =>
                offlineEvalMetricInfos.get(metricinfoid).map { metricInfo =>
                  offlineEvalSplitterInfos.get(splitterinfoid).map { splitterInfo =>

                    // delete previous offlinetune stuff if the algo's offlinetuneid != None
                    if (updatedAlgo.offlinetuneid != None) {
                      val tuneid = updatedAlgo.offlinetuneid.get

                      offlineTunes.get(tuneid) map { tune =>
                        /** Make sure to unset offline tune's creation time to prevent scheduler from picking up */
                        offlineTunes.update(tune.copy(createtime = None))

                        // TODO: check scheduler err
                        Helper.stopAndDeleteOfflineTuneScheduler(appid.toInt, engineid.toInt, tuneid)
                        Future {
                          Helper.deleteOfflineTune(tuneid, keepSettings = false)
                        }
                      }
                    }

                    // auto tune
                    algos.update(updatedAlgo)

                    // create an OfflineTune and paramGen
                    val offlineTune = OfflineTune(
                      id = -1,
                      engineid = updatedAlgo.engineid,
                      loops = 5, // TODO: default 5 now
                      createtime = None, // NOTE: no createtime yet
                      starttime = None,
                      endtime = None
                    )

                    val tuneid = offlineTunes.insert(offlineTune)
                    Logger.info("Create offline tune ID " + tuneid)

                    paramGens.insert(ParamGen(
                      id = -1,
                      infoid = "random", // TODO: default random scan param gen now
                      tuneid = tuneid,
                      params = Map() // TODO: param for param gen
                    ))

                    // update original algo status to tuning
                    algos.update(updatedAlgo.copy(
                      offlinetuneid = Some(tuneid),
                      status = "tuning"
                    ))

                    val baseLineAlgo = Algo(
                      id = -1,
                      engineid = updatedAlgo.engineid,
                      name = "Default-BasedLine-Algo-for-OfflineTune-" + tuneid,
                      infoid = baseLineAlgoInfo.id,
                      command = "",
                      params = baseLineAlgoInfo.params.mapValues(_.defaultvalue),
                      settings = Map(), // no use for now
                      modelset = false, // init value
                      createtime = DateTime.now,
                      updatetime = DateTime.now,
                      status = "simeval",
                      offlineevalid = None,
                      offlinetuneid = Some(tuneid),
                      loop = Some(0), // loop=0 reserved for autotune baseline
                      paramset = None
                    )

                    val metric = OfflineEvalMetric(
                      id = 0,
                      infoid = metricInfo.id,
                      evalid = 0, // will be assigned later
                      params = metricInfo.params.mapValues(_.defaultvalue)
                    )

                    // percentage param is standard for all splitter
                    // TODO: hardcode percentage for auto tune for now. get from UI
                    val percentageParam = Map(
                      "trainingPercent" -> 0.55,
                      "validationPercent" -> 0.2,
                      "testPercent" -> 0.2
                    )

                    val splitter = OfflineEvalSplitter(
                      id = 0,
                      evalid = 0, // will be assigned later
                      name = "", // will be assigned later
                      infoid = splitterInfo.id,
                      settings = splitterInfo.params.mapValues(_.defaultvalue) ++ percentageParam
                    )

                    // TODO: get iterations, metric info, etc from UI, now hardcode to 3.
                    for (i <- 1 to 3) {
                      createOfflineEval(engine, List(baseLineAlgo), List(metric), splitter, 1, Some(tuneid))
                    }

                    // after everything has setup,
                    // update with createtime, so scheduler can know it's ready to be picked up
                    offlineTunes.update(offlineTune.copy(
                      id = tuneid,
                      createtime = Some(DateTime.now)
                    ))

                    // call sync to scheduler here
                    WS.url(settingsSchedulerUrl + "/users/" + user.id + "/sync").get()

                    Ok

                  }.getOrElse(InternalServerError(Json.obj("message" -> s"OfflineEvalSplitterInfo ID ${splitterinfoid} not found.")))
                }.getOrElse(InternalServerError(Json.obj("message" -> s"OfflineEvalMetricInfo ID ${metricinfoid} not found.")))
              }.getOrElse(InternalServerError(Json.obj("message" -> s"AlgoInfo ID ${defaultBaseLineAlgoType} not found.")))
            }.getOrElse(InternalServerError(Json.obj("message" -> s"EngineInfo ID ${engine.infoid} not found.")))
          }
        })
  }

  def getAlgoTemplateHtml(appid: Int, engineid: Int, algoid: Int) = WithAlgo(appid, engineid, algoid) { (user, app, engine, algo) =>
    implicit request =>
      algoInfos.get(algo.infoid) map { algoInfo =>
        if (algoInfo.paramsections.isEmpty)
          Ok(views.html.algos.template(handleParamSections[AlgoInfo](Seq(ParamSection(
            name = "Parameter Settings",
            description = Some("No extra setting is required for this algorithm."))), algoInfo, 1), true))
        else
          Ok(views.html.algos.template(handleParamSections[AlgoInfo](algoInfo.paramsections, algoInfo, 1), false))
      } getOrElse {
        NotFound(s"AlgoInfo ID ${algo.infoid} not found")
      }
  }

  def getAlgoTemplateJs(appid: Int, engineid: Int, algoid: Int) = WithAlgo(appid, engineid, algoid) { (user, app, engine, algo) =>
    implicit request =>
      algoInfos.get(algo.infoid) map { algoInfo =>
        Ok(views.js.algos.template(
          algoInfo.id,
          algoInfo.paramsections.map(collectParams(algoInfo, _)).foldLeft(Set[Param]())(_ ++ _).toSeq,
          algoInfo.paramsections.map(collectParams(algoInfo, _, true)).foldLeft(Set[Param]())(_ ++ _).toSeq))
      } getOrElse {
        NotFound(s"AlgoInfo ID ${algo.infoid} not found")
      }
  }

  def getMetricInfoTemplateHtml(engineinfoid: String, metricinfoid: String) = WithUser { user =>
    implicit request =>
      offlineEvalMetricInfos.get(metricinfoid) map { metricInfo =>
        if (metricInfo.paramsections.isEmpty)
          Ok(views.html.metrics.template(handleParamSections[OfflineEvalMetricInfo](Seq(ParamSection(
            name = "Parameter Settings",
            description = Some("No extra setting is required for this metric."))), metricInfo, 1), true))
        else
          Ok(views.html.metrics.template(handleParamSections[OfflineEvalMetricInfo](metricInfo.paramsections, metricInfo, 1), false))
      } getOrElse {
        NotFound(s"OfflineEvalMetricInfo ID ${metricinfoid} not found")
      }
  }

  def getSplitterInfoTemplateHtml(engineinfoid: String, splitterinfoid: String) = WithUser { user =>
    implicit request =>
      offlineEvalSplitterInfos.get(splitterinfoid) map { splitterInfo =>
        if (splitterInfo.paramsections.isEmpty)
          Ok(views.html.splitters.template(handleParamSections[OfflineEvalSplitterInfo](Seq(ParamSection(
            name = "Parameter Settings",
            description = Some("No extra setting is required for this splitter."))), splitterInfo, 1), true))
        else
          Ok(views.html.splitters.template(handleParamSections[OfflineEvalSplitterInfo](splitterInfo.paramsections, splitterInfo, 1), false))
      } getOrElse {
        NotFound(s"OfflineEvalSplitterInfo ID ${splitterinfoid} not found")
      }
  }

  def collectParams[T <: Info](info: T, paramsection: ParamSection, tuning: Boolean = false): Set[Param] = {
    if (tuning)
      if (paramsection.sectiontype == "tuning")
        paramsection.params.map(_.map(info.params(_)).toSet).getOrElse(Set()) ++
          paramsection.subsections.map(_.map(collectParams(info, _, false)).reduce(_ ++ _)).getOrElse(Set())
      else
        paramsection.subsections.map(_.map(collectParams(info, _, tuning)).reduce(_ ++ _)).getOrElse(Set())
    else
      paramsection.params.map(_.map(info.params(_)).toSet).getOrElse(Set()) ++
        paramsection.subsections.map(_.map(collectParams(info, _, tuning)).reduce(_ ++ _)).getOrElse(Set())
  }

  def handleParam[T <: Info](param: Param, info: T, tuning: Boolean = false): String = {
    val content = param.ui.uitype match {
      case "selection" =>
        uiSelection[T](info, param.id, param.name, param.description, param.ui.selections.map(_.map(s => (s.name, s.value))).get, Some(param.defaultvalue.toString))
      case "slider" =>
        uiSlider[T](info, param.id, param.name, param.description)
      case _ =>
        if (tuning)
          uiTextPair[T](info, param.id + "Min", param.id + "Max", param.name, param.description)
        else
          uiText[T](info, param.id, param.name, param.description, Some(param.defaultvalue.toString))
    }
    content.toString
  }

  def handleParamSections[T <: Info](paramsections: Seq[ParamSection], info: T, level: Int, tuning: Boolean = false): String = {
    paramsections map { paramsection =>
      if (paramsection.sectiontype == "tuning")
        views.html.algos.sectionmanualauto(
          paramsection.name,
          handleParamSectionContent[T](paramsection, info, level),
          handleParamSectionContent[T](paramsection, info, level, true))
      else if (level > 1)
        uiSection2[T](info, paramsection.name, paramsection.description, handleParamSectionContent[T](paramsection, info, level, tuning))
      else
        uiSection1[T](info, paramsection.name, paramsection.description, handleParamSectionContent[T](paramsection, info, level, tuning))
    } mkString ""
  }

  def handleParamSectionContent[T <: Info](paramsection: ParamSection, info: T, level: Int, tuning: Boolean = false) = {
    paramsection.params.map(_.map(p => handleParam[T](info.params(p), info, tuning)).mkString).getOrElse("") +
      paramsection.subsections.map(handleParamSections[T](_, info, level + 1, tuning)).getOrElse("")
  }

  def uiText[T <: Info](info: T, id: String, name: String, description: Option[String], defaultValue: Option[String] = None) = {
    info match {
      case _: AlgoInfo =>
        views.html.algos.text(id, name, description)
      case _: EngineInfo =>
        views.html.engines.text(id, name, description)
      case _: OfflineEvalMetricInfo =>
        views.html.metrics.text(id, name, description, defaultValue)
      case _: OfflineEvalSplitterInfo =>
        views.html.splitters.text(id, name, description, defaultValue)
    }
  }

  def uiTextPair[T <: Info](info: T, id1: String, id2: String, name: String, description: Option[String]) = {
    views.html.algos.textpair(id1, id2, name, description)
  }

  def uiSlider[T <: Info](info: T, id: String, name: String, description: Option[String]) = {
    views.html.engines.slider(id, name, description)
  }

  def uiSelection[T <: Info](info: T, id: String, name: String, description: Option[String], selections: Seq[(String, String)], defaultValue: Option[String] = None) = {
    info match {
      case _: AlgoInfo =>
        views.html.algos.selection(id, name, description, selections)
      case _: EngineInfo =>
        views.html.engines.selection(id, name, description, selections)
      case _: OfflineEvalMetricInfo =>
        views.html.metrics.selection(id, name, description, selections, defaultValue)
      case _: OfflineEvalSplitterInfo =>
        views.html.splitters.selection(id, name, description, selections, defaultValue)
    }
  }

  def uiSection1[T <: Info](info: T, name: String, description: Option[String], content: String) = {
    info match {
      case _: AlgoInfo =>
        views.html.algos.section1(name, description, content)
      case _: EngineInfo =>
        views.html.engines.section1(name, description, content)
      case _: OfflineEvalMetricInfo =>
        views.html.metrics.section1(name, description, content)
      case _: OfflineEvalSplitterInfo =>
        views.html.splitters.section1(name, description, content)
    }
  }

  def uiSection2[T <: Info](info: T, name: String, description: Option[String], content: String) = {
    info match {
      case _: AlgoInfo =>
        views.html.algos.section2(name, description, content)
      case _: EngineInfo =>
        views.html.engines.section2(name, description, content)
      // OfflineEvalMetricInfo doesn't support section2
      // OfflineEvalSplitterInfo doesn't support section2
    }
  }
}
