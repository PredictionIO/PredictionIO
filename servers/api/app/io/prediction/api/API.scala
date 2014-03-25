package io.prediction.api

import io.prediction.commons._
import io.prediction.commons.appdata.{ Item, U2IAction, User }
import io.prediction.commons.settings.{ App, Engine }
import io.prediction.output.AlgoOutputSelector

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data.validation._
import play.api.i18n._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.libs.iteratee.Enumerator
import play.api.Play.current

//import com.codahale.jerkson.Json._
import org.joda.time._
import org.joda.time.format._

object API extends Controller {
  /** Set up commons. */
  val config = new Config()

  val apps = config.getSettingsApps()
  val engines = config.getSettingsEngines()
  val algos = config.getSettingsAlgos()

  val users = config.getAppdataUsers()
  val items = config.getAppdataItems()
  val u2iActions = config.getAppdataU2IActions()

  /** Set up output. */
  val algoOutputSelector = new AlgoOutputSelector(algos)

  val notFound = NotFound("Your request is not supported.")

  /** Implicits for JSON conversion. */
  trait APIResponse {
    def status: Int
  }
  case class APIMessageResponse(status: Int, body: Map[String, Any]) extends APIResponse
  case class APIUserResponse(status: Int, user: User) extends APIResponse
  case class APIItemResponse(status: Int, item: Item) extends APIResponse
  case class APIErrors(errors: Seq[Map[String, String]])

  implicit object APIResponseToJson extends Writes[APIResponse] {
    def writes(r: APIResponse) = r match {
      case msg: APIMessageResponse => Json.toJson(msg.asInstanceOf[APIMessageResponse].body.mapValues { anyToJsValue(_) })
      case user: APIUserResponse => Json.toJson(user.asInstanceOf[APIUserResponse].user)
      case item: APIItemResponse => Json.toJson(item.asInstanceOf[APIItemResponse].item)
    }
  }

  implicit object APIErrorsToJson extends Writes[APIErrors] {
    def writes(e: APIErrors) = {
      Json.toJson(e.errors)
    }
  }

  implicit object UserToJson extends Writes[User] {
    def writes(user: User) =
      Json.obj(
        "pio_uid" -> user.id) ++
        //"pio_ct" -> user.ct) ++
        (user.latlng map { l => Json.obj("pio_latlng" -> Json.arr(l._1, l._2)) } getOrElse emptyJsonObj) ++
        (user.inactive map { i => Json.obj("pio_inactive" -> i) } getOrElse emptyJsonObj) ++
        (user.attributes.map { a => JsObject((a mapValues { anyToJsValue(_) }).toSeq) } getOrElse emptyJsonObj)
    //(user.attributes.map { a => Json.obj("attributes" -> Json.toJson(a mapValues { anyToJsValue(_) })) } getOrElse emptyJsonObj)
  }

  implicit object ItemToJson extends Writes[Item] {
    def writes(item: Item) =
      Json.obj(
        "pio_iid" -> item.id,
        //"pio_ct" -> item.ct,
        "pio_itypes" -> item.itypes) ++
        (item.starttime map { v => Json.obj("pio_startT" -> v) } getOrElse emptyJsonObj) ++
        (item.endtime map { v => Json.obj("pio_endT" -> v) } getOrElse emptyJsonObj) ++
        (item.price map { v => Json.obj("pio_price" -> v) } getOrElse emptyJsonObj) ++
        (item.profit map { v => Json.obj("pio_profit" -> v) } getOrElse emptyJsonObj) ++
        (item.latlng map { v => Json.obj("pio_latlng" -> latlngToList(v)) } getOrElse emptyJsonObj) ++
        (item.inactive map { v => Json.obj("pio_inactive" -> v) } getOrElse emptyJsonObj) ++
        (item.attributes.map { a => JsObject((a mapValues { anyToJsValue(_) }).toSeq) } getOrElse emptyJsonObj)
    //(item.attributes.map { a => Json.obj("attributes" -> Json.toJson(a mapValues { anyToJsValue(_) })) } getOrElse emptyJsonObj)
  }

  def anyToJsValue(v: Any): JsValue = v match {
    case x: Int => Json.toJson(v.asInstanceOf[Int])
    case x: String => Json.toJson(v.asInstanceOf[String])
    case x: Seq[_] => Json.toJson(v.asInstanceOf[Seq[String]])
    case x: APIErrors => Json.toJson(v.asInstanceOf[APIErrors])
    case _ => JsNull
  }

  /** Control structures used by the API. */
  def FormattedResponse(format: String)(r: APIResponse) = {
    format match {
      case "json" => (new Status(r.status)(Json.stringify(Json.toJson(r)))).as(JSON)
      case "png" => Play.resourceAsStream("public/images/spacer.png") map { stream =>
        val fileContent: Enumerator[Array[Byte]] = Enumerator.fromStream(stream)
        SimpleResult(
          header = ResponseHeader(200),
          body = fileContent)
      } getOrElse notFound
      case _ => notFound
    }
  }

  def AuthenticatedApp(appkey: String)(f: App => APIResponse) = {
    apps.getByAppkey(appkey) map { f(_) } getOrElse APIMessageResponse(FORBIDDEN, Map("message" -> "Invalid appkey."))
  }

  def ValidEngine(enginename: String)(f: Engine => APIResponse)(implicit app: App) = {
    engines.getByAppidAndName(app.id, enginename) map { f(_) } getOrElse APIMessageResponse(
      NOT_FOUND,
      Map(
        "message" -> (enginename + " is not a valid engine. Please make sure it is defined in your app's control panel.")
      )
    )
  }

  /**
   * In order to override default error messages, use Lang("en") for
   * Messages() to enforce framwork to use conf/messages.en because
   * default messages cannot be overridden by simply using conf/messages
   * without specifying a language.
   */
  def bindFailed(loe: Seq[FormError]) = APIMessageResponse(
    BAD_REQUEST,
    Map(
      "errors" -> APIErrors(loe.map(e => Map("field" -> e.key, "message" -> Messages(e.message, e.args: _*)(Lang("en")))))
    )
  )

  /** Form validation constraints. */
  val numeric: Mapping[String] = of[String] verifying Constraints.pattern("""-?\d+(\.\d*)?""".r, "numeric", "Must be a number.")
  val gender: Mapping[String] = of[String] verifying Constraints.pattern("""[MmFf]""".r, "gender", "Must be either 'M' or 'F'.")
  val listOfInts: Mapping[String] = of[String] verifying Constraint[String]("listOfInts") {
    o =>
      {
        try {
          o.split(",").map { _.toInt }
          Valid
        } catch {
          case _: Throwable => Invalid(ValidationError("Must be a list of integers separated by commas."))
        }
      }
  }
  val itypes: Mapping[String] = of[String] verifying Constraint[String]("itypes") { o =>
    """[^\t]+""".r.unapplySeq(o) map { _ =>
      val splitted = o.split(",")
      if (splitted.size == 0)
        Invalid(ValidationError("Must specify at least one valid item type."))
      else if (splitted.exists(_.size == 0))
        Invalid(ValidationError("Must not contain any empty item types."))
      else
        Valid
    } getOrElse (Invalid(ValidationError("Must not contain any tab characters.")))
  }
  val latlngRegex = """-?\d+(\.\d*)?,-?\d+(\.\d*)?""".r
  val latlng: Mapping[String] = of[String] verifying Constraint[String]("latlng", () => latlngRegex) {
    o =>
      latlngRegex.unapplySeq(o).map(_ => {
        val coord = o.split(",") map { _.toDouble }
        if (coord(0) >= -90 && coord(0) < 90 && coord(1) >= -180 && coord(1) < 180) Valid
        else Invalid(ValidationError("Cooordinates exceed valid range (-90 <= lat < 90,-180 <= long < 180)."))
      }).getOrElse(Invalid(ValidationError("Must be in the format of '<latitude>,<longitude>'.")))
  }
  val timestamp: Mapping[String] = of[String] verifying Constraint[String]("timestamp") {
    o =>
      {
        try {
          o.toLong
          Valid
        } catch {
          case e: RuntimeException => try {
            ISODateTimeFormat.dateTimeParser().parseDateTime(o)
            Valid
          } catch {
            case e: IllegalArgumentException => Invalid(ValidationError("Must either be a Unix time in milliseconds, or an ISO 8601 date and time."))
          }
        }
      }
  }
  val date: Mapping[String] = of[String] verifying Constraint[String]("date") {
    o =>
      {
        try {
          o.toLong
          Valid
        } catch {
          case _: Throwable => try {
            ISODateTimeFormat.localDateParser().parseLocalDate(o)
            Valid
          } catch {
            case _: Throwable => Invalid(ValidationError("Must be an ISO 8601 date."))
          }
        }
      }
  }

  /** Utilties. */
  val emptyMap = Map()
  val emptyJsonObj = Json.obj()

  def parseLatlng(latlng: String): Tuple2[Double, Double] = {
    val splitted = latlng.split(",")
    (splitted(0).toDouble, splitted(1).toDouble)
  }

  def latlngToList(latlng: Tuple2[Double, Double]): List[Double] = List(latlng._1, latlng._2)

  /** Accepts UNIX timestamp, ISO 8601 time format, with optional timezone conversion from app settings. */
  def parseDateTimeFromString(timestring: String)(implicit app: App) = {
    try {
      new DateTime(timestring.toLong)
    } catch {
      case e: RuntimeException => try {
        val dt = ISODateTimeFormat.localDateOptionalTimeParser.parseLocalDateTime(timestring)
        dt.toDateTime(DateTimeZone.forID(app.timezone))
      } catch {
        case e: IllegalArgumentException => ISODateTimeFormat.dateTimeParser.parseDateTime(timestring)
      }
    }
  }

  /** API. */
  def status = Action {
    Ok("PredictionIO Output API is online.")
  }

  def createUser(format: String) = Action { implicit request =>
    FormattedResponse(format) {
      Attributes(tuple(
        "pio_appkey" -> nonEmptyText,
        "pio_uid" -> nonEmptyText,
        "pio_latlng" -> optional(latlng),
        "pio_inactive" -> optional(boolean)
      ), Set( // all reserved attributes
        "pio_appkey",
        "pio_ct",
        "pio_uid",
        "pio_latlng",
        "pio_inactive"
      )).bindFromRequestAndFold(
        f => bindFailed(f.errors),
        (t, attributes) => {
          val (appkey, uid, latlng, inactive) = t
          if (attributes.keys.exists(_.contains("."))) {
            APIMessageResponse(BAD_REQUEST, Map("message" -> "Optional Parameters, Parameter keys cannot contain dots (i.e. .)"))
          } else {
            AuthenticatedApp(t._1) { app =>
              users.insert(User(
                id = uid,
                appid = app.id,
                ct = DateTime.now,
                latlng = latlng map { parseLatlng(_) },
                inactive = inactive,
                attributes = if (attributes.isEmpty) None else Some(attributes)
              ))
              APIMessageResponse(CREATED, Map("message" -> "User created."))
            }
          }
        }
      )
    }
  }

  def getUser(format: String, uid: String) = Action { implicit request =>
    FormattedResponse(format) {
      Form("pio_appkey" -> nonEmptyText).bindFromRequest.fold(
        f => bindFailed(f.errors),
        t => AuthenticatedApp(t) { app =>
          users.get(app.id, uid) map { user =>
            APIUserResponse(OK, user)
          } getOrElse APIMessageResponse(NOT_FOUND, Map("message" -> "Cannot find user."))
        }
      )
    }
  }

  def deleteUser(format: String, uid: String) = Action { implicit request =>
    FormattedResponse(format) {
      Form("pio_appkey" -> nonEmptyText).bindFromRequest.fold(
        f => bindFailed(f.errors),
        t => AuthenticatedApp(t) { app =>
          users.delete(app.id, uid)
          APIMessageResponse(OK, Map("message" -> "User deleted."))
        }
      )
    }
  }

  def createItem(format: String) = Action { implicit request =>
    FormattedResponse(format) {
      Attributes(tuple(
        "pio_appkey" -> nonEmptyText,
        "pio_iid" -> nonEmptyText,
        "pio_itypes" -> itypes,
        "pio_price" -> optional(numeric),
        "pio_profit" -> optional(numeric),
        "pio_startT" -> optional(timestamp),
        "pio_endT" -> optional(timestamp),
        "pio_latlng" -> optional(latlng),
        "pio_inactive" -> optional(boolean)
      ), Set( // all reserved attributes
        "pio_appkey",
        "pio_ct",
        "pio_iid",
        "pio_itypes",
        "pio_price",
        "pio_profit",
        "pio_startT",
        "pio_endT",
        "pio_latlng",
        "pio_inactive"
      )).bindFromRequestAndFold(
        f => bindFailed(f.errors),
        (t, attributes) => {
          val (appkey, iid, itypes, price, profit, startT, endT, latlng, inactive) = t
          AuthenticatedApp(appkey) { implicit app =>
            items.insert(Item(
              id = iid,
              appid = app.id,
              ct = DateTime.now,
              itypes = itypes.split(",").toList,
              starttime = startT map { t => Some(parseDateTimeFromString(t)) } getOrElse Some(DateTime.now),
              endtime = endT map { parseDateTimeFromString(_) },
              price = price map { _.toDouble },
              profit = profit map { _.toDouble },
              latlng = latlng map { parseLatlng(_) },
              inactive = inactive,
              attributes = if (attributes.isEmpty) None else Some(attributes)
            ))
            APIMessageResponse(CREATED, Map("message" -> "Item created."))
          }
        }
      )
    }
  }

  def getItem(format: String, iid: String) = Action { implicit request =>
    FormattedResponse(format) {
      Form("pio_appkey" -> nonEmptyText).bindFromRequest.fold(
        f => bindFailed(f.errors),
        t => AuthenticatedApp(t) { app =>
          items.get(app.id, iid) map { item =>
            APIItemResponse(OK, item)
          } getOrElse APIMessageResponse(NOT_FOUND, Map("message" -> "Cannot find item."))
        }
      )
    }
  }

  def deleteItem(format: String, iid: String) = Action { implicit request =>
    FormattedResponse(format) {
      Form("pio_appkey" -> nonEmptyText).bindFromRequest.fold(
        f => bindFailed(f.errors),
        t => AuthenticatedApp(t) { app =>
          items.delete(app.id, iid)
          APIMessageResponse(OK, Map("message" -> "Item deleted."))
        }
      )
    }
  }

  /** unified user to item action handler */
  def userToItemAction(format: String) = Action { implicit request =>
    FormattedResponse(format) {
      Form(tuple(
        "pio_appkey" -> nonEmptyText,
        "pio_action" -> nonEmptyText,
        "pio_uid" -> nonEmptyText,
        "pio_iid" -> nonEmptyText,
        "pio_t" -> optional(timestamp),
        "pio_latlng" -> optional(latlng),
        "pio_rate" -> optional(number(1, 5)),
        "pio_price" -> optional(numeric)
      )).bindFromRequest.fold(
        f => bindFailed(f.errors),
        fdata => AuthenticatedApp(fdata._1) { implicit app =>
          val (appkey, action, uid, iid, t, latlng, rate, price) = fdata

          val vValue: Option[Int] = action match {
            case "rate" => rate
            case _ => None
          }
          val validActions = List(u2iActions.rate, u2iActions.like, u2iActions.dislike, u2iActions.view, u2iActions.conversion)

          // additional user input checking
          if ((action == u2iActions.rate) && (vValue == None)) {
            APIMessageResponse(BAD_REQUEST, Map("errors" -> APIErrors(Seq(Map("field" -> "pio_rate", "message" -> "Required for rate action.")))))
          } else if (!validActions.contains(action)) {
            APIMessageResponse(BAD_REQUEST, Map("errors" -> APIErrors(Seq(Map("field" -> "pio_action", "message" -> "Custom action is not supported yet.")))))
          } else {

            u2iActions.insert(U2IAction(
              appid = app.id,
              action = action,
              uid = uid,
              iid = iid,
              t = t map { parseDateTimeFromString(_) } getOrElse DateTime.now,
              latlng = latlng map { parseLatlng(_) },
              v = vValue,
              price = price map { _.toDouble }
            ))
            APIMessageResponse(CREATED, Map("message" -> ("Action " + action + " recorded.")))
          }
        }
      )
    }
  }

  /** legacy API for pixel tracking, no prefix pio_ */
  def createUserLegacy(format: String) = Action { implicit request =>
    FormattedResponse(format) {
      Attributes(tuple(
        "appkey" -> nonEmptyText,
        "uid" -> nonEmptyText,
        "latlng" -> optional(latlng),
        "inactive" -> optional(boolean)
      ), Set( // all reserved attributes
        "appkey",
        "ct",
        "uid",
        "latlng",
        "inactive"
      )).bindFromRequestAndFold(
        f => bindFailed(f.errors),
        (t, attributes) => {
          val (appkey, uid, latlng, inactive) = t
          AuthenticatedApp(t._1) { app =>
            users.insert(User(
              id = uid,
              appid = app.id,
              ct = DateTime.now,
              latlng = latlng map { parseLatlng(_) },
              inactive = inactive,
              attributes = if (attributes.isEmpty) None else Some(attributes)
            ))
            APIMessageResponse(CREATED, Map("message" -> "User created."))
          }
        }
      )
    }
  }

  def createItemLegacy(format: String) = Action { implicit request =>
    FormattedResponse(format) {
      Attributes(tuple(
        "appkey" -> nonEmptyText,
        "iid" -> nonEmptyText,
        "itypes" -> itypes,
        "price" -> optional(numeric),
        "profit" -> optional(numeric),
        "startT" -> optional(timestamp),
        "endT" -> optional(timestamp),
        "latlng" -> optional(latlng),
        "inactive" -> optional(boolean)
      ), Set( // all reserved attributes
        "appkey",
        "ct",
        "iid",
        "itypes",
        "price",
        "profit",
        "startT",
        "endT",
        "latlng",
        "inactive"
      )).bindFromRequestAndFold(
        f => bindFailed(f.errors),
        (t, attributes) => {
          val (appkey, iid, itypes, price, profit, startT, endT, latlng, inactive) = t
          AuthenticatedApp(appkey) { implicit app =>
            items.insert(Item(
              id = iid,
              appid = app.id,
              ct = DateTime.now,
              itypes = itypes.split(",").toList,
              starttime = startT map { t => Some(parseDateTimeFromString(t)) } getOrElse Some(DateTime.now),
              endtime = endT map { parseDateTimeFromString(_) },
              price = price map { _.toDouble },
              profit = profit map { _.toDouble },
              latlng = latlng map { parseLatlng(_) },
              inactive = inactive,
              attributes = if (attributes.isEmpty) None else Some(attributes)
            ))
            APIMessageResponse(CREATED, Map("message" -> "Item created."))
          }
        }
      )
    }
  }

  def userToItemActionLegacy(format: String, action: String) = Action { implicit request =>
    FormattedResponse(format) {
      Form(tuple(
        "appkey" -> nonEmptyText,
        //"action" -> nonEmptyText,
        "uid" -> nonEmptyText,
        "iid" -> nonEmptyText,
        "t" -> optional(timestamp),
        "latlng" -> optional(latlng),
        "rate" -> optional(number(1, 5)),
        "price" -> optional(numeric)
      )).bindFromRequest.fold(
        f => bindFailed(f.errors),
        fdata => AuthenticatedApp(fdata._1) { implicit app =>
          val (appkey, uid, iid, t, latlng, rate, price) = fdata

          val vValue: Option[Int] = action match {
            case "rate" => rate
            case _ => None
          }
          val validActions = List(u2iActions.rate, u2iActions.like, u2iActions.dislike, u2iActions.view, u2iActions.conversion)

          // additional user input checking
          if ((action == u2iActions.rate) && (vValue == None)) {
            APIMessageResponse(BAD_REQUEST, Map("errors" -> APIErrors(Seq(Map("field" -> "rate", "message" -> "Required for rate action.")))))
          } else if (!validActions.contains(action)) {
            APIMessageResponse(BAD_REQUEST, Map("errors" -> APIErrors(Seq(Map("field" -> "action", "message" -> "Custom action is not supported yet.")))))
          } else {

            u2iActions.insert(U2IAction(
              appid = app.id,
              action = action,
              uid = uid,
              iid = iid,
              t = t map { parseDateTimeFromString(_) } getOrElse DateTime.now,
              latlng = latlng map { parseLatlng(_) },
              v = vValue,
              price = price map { _.toDouble }
            ))
            APIMessageResponse(CREATED, Map("message" -> ("Action " + action + " recorded.")))
          }
        }
      )
    }
  }

  /** item rec topN */
  def itemRecTopN(format: String, enginename: String) = Action { implicit request =>
    FormattedResponse(format) {
      Form(tuple(
        "pio_appkey" -> nonEmptyText,
        "pio_uid" -> nonEmptyText,
        "pio_n" -> number(1, 100),
        "pio_itypes" -> optional(itypes),
        "pio_latlng" -> optional(latlng),
        "pio_within" -> optional(numeric),
        "pio_unit" -> optional(text),
        "pio_attributes" -> optional(text)
      )).bindFromRequest.fold(
        f => bindFailed(f.errors),
        t => {
          val (appkey, uid, n, itypes, latlng, within, unit, attributes) = t
          AuthenticatedApp(appkey) { implicit app =>
            ValidEngine(enginename) { implicit engine =>
              try {
                val res = algoOutputSelector.itemRecSelection(
                  uid = uid,
                  n = n,
                  itypes = itypes map { _.split(",") },
                  latlng = latlng map { latlng =>
                    val ll = latlng.split(",")
                    (ll(0).toDouble, ll(1).toDouble)
                  },
                  within = within map { _.toDouble },
                  unit = unit
                )
                if (res.length > 0) {
                  val attributesToGet: Seq[String] = attributes map { _.split(",").toSeq } getOrElse Seq()

                  if (attributesToGet.length > 0) {
                    val attributedItems = items.getByIds(app.id, res).map(i => (i.id, i)).toMap
                    val ar = attributesToGet map { atg =>
                      Map(atg -> res.map(ri =>
                        attributedItems(ri).attributes map { attribs =>
                          attribs.get(atg) getOrElse null
                        } getOrElse null
                      ))
                    }

                    APIMessageResponse(OK, Map("pio_iids" -> res) ++ ar.reduceLeft((a, b) => a ++ b))
                  } else {
                    APIMessageResponse(OK, Map("pio_iids" -> res))
                  }
                } else {
                  APIMessageResponse(NOT_FOUND, Map("message" -> "Cannot find recommendation for user."))
                }
              } catch {
                case e: Exception =>
                  APIMessageResponse(INTERNAL_SERVER_ERROR, Map("message" -> e.getMessage()))
              }
            }
          }
        }
      )
    }
  }

  def itemSimTopN(format: String, enginename: String) = Action { implicit request =>
    FormattedResponse(format) {
      Form(tuple(
        "pio_appkey" -> nonEmptyText,
        "pio_iid" -> nonEmptyText,
        "pio_n" -> number(1, 100),
        "pio_itypes" -> optional(itypes),
        "pio_latlng" -> optional(latlng),
        "pio_within" -> optional(numeric),
        "pio_unit" -> optional(text),
        "pio_attributes" -> optional(text)
      )).bindFromRequest.fold(
        f => bindFailed(f.errors),
        t => {
          val (appkey, iid, n, itypes, latlng, within, unit, attributes) = t
          AuthenticatedApp(appkey) { implicit app =>
            ValidEngine(enginename) { implicit engine =>
              try {
                val res = algoOutputSelector.itemSimSelection(
                  iid = iid,
                  n = n,
                  itypes = itypes map { _.split(",") },
                  latlng = latlng map { latlng =>
                    val ll = latlng.split(",")
                    (ll(0).toDouble, ll(1).toDouble)
                  },
                  within = within map { _.toDouble },
                  unit = unit
                )
                if (res.length > 0) {
                  val attributesToGet: Seq[String] = attributes map { _.split(",").toSeq } getOrElse Seq()

                  if (attributesToGet.length > 0) {
                    val attributedItems = items.getByIds(app.id, res).map(i => (i.id, i)).toMap
                    val ar = attributesToGet map { atg =>
                      Map(atg -> res.map(ri =>
                        attributedItems(ri).attributes map { attribs =>
                          attribs.get(atg) getOrElse null
                        } getOrElse null
                      ))
                    }

                    APIMessageResponse(OK, Map("pio_iids" -> res) ++ ar.reduceLeft((a, b) => a ++ b))
                  } else {
                    APIMessageResponse(OK, Map("pio_iids" -> res))
                  }
                } else {
                  APIMessageResponse(NOT_FOUND, Map("message" -> "Cannot find similar items for item."))
                }
              } catch {
                case e: Exception =>
                  APIMessageResponse(INTERNAL_SERVER_ERROR, Map("message" -> e.getMessage(), "trace" -> e.getStackTrace().map(_.toString).mkString("\n")))
              }
            }
          }
        }
      )
    }
  }
}
