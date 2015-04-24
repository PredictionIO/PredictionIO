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

package io.prediction.data.webhooks.segmentio

import io.prediction.data.webhooks.{ConnectorException, JsonConnector}
import org.json4s._

private[prediction] object SegmentIOConnector extends JsonConnector {

  implicit val json4sFormats: Formats = DefaultFormats

  override
  def toEventJson(data: JObject): JObject = {
    // TODO: check segmentio API version

    val common = try {
      data.extract[Common]
    } catch {
      case e: Throwable ⇒ throw new ConnectorException(
        s"Cannot extract Common field from $data. ${e.getMessage}", e
      )
    }

    try {
      common.`type` match {
        case "identify" ⇒
          toEventJson(
            common = common,
            identify = data.extract[Events.Identify]
          )

        case "track" ⇒
          toEventJson(
            common = common,
            track = data.extract[Events.Track]
          )

        case "alias" ⇒
          toEventJson(
            common = common,
            alias = data.extract[Events.Alias]
          )

        case "page" ⇒
          toEventJson(
            common = common,
            page = data.extract[Events.Page]
          )

        case "screen" ⇒
          toEventJson(
            common = common,
            screen = data.extract[Events.Screen]
          )

        case "group" ⇒
          toEventJson(
            common = common,
            group = data.extract[Events.Group]
          )

        case _ ⇒
          throw new ConnectorException(
            s"Cannot convert unknown type ${common.`type`} to event JSON."
          )
      }
    } catch {
      case e: ConnectorException => throw e
      case e: Exception =>
        throw new ConnectorException(
          s"Cannot convert $data to event JSON. ${e.getMessage}", e
        )
    }
  }

  def toEventJson(common: Common, identify: Events.Identify ): JObject = {
    import org.json4s.JsonDSL._
    val eventProperties = "traits" → identify.traits
    toJson(common, eventProperties)
  }

  def toEventJson(common: Common, track: Events.Track): JObject = {
    import org.json4s.JsonDSL._
    val eventProperties =
      ("properties" → track.properties) ~
      ("event" → track.event)
    toJson(common, eventProperties)
  }

  def toEventJson(common: Common, alias: Events.Alias): JObject = {
    import org.json4s.JsonDSL._
    toJson(common, "previousId" → alias.previousId)
  }

  def toEventJson(common: Common, screen: Events.Screen): JObject = {
    import org.json4s.JsonDSL._
    val eventProperties =
      ("name" → screen.name) ~
      ("properties" → screen.properties)
    toJson(common, eventProperties)
  }

  def toEventJson(common: Common, page: Events.Page): JObject = {
    import org.json4s.JsonDSL._
    val eventProperties =
      ("name" → page.name) ~
      ("properties" → page.properties)
    toJson(common, eventProperties)
  }

  def toEventJson(common: Common, group: Events.Group): JObject = {
    import org.json4s.JsonDSL._
    val eventProperties =
      ("groupId" → group.groupId) ~
      ("traits" → group.traits)
    toJson(common, eventProperties)
  }

  private def toJson(common: Common, props: JObject): JsonAST.JObject = {
    val commonFields = commonToJson(common)
    JObject(("properties" → properties(common, props)) :: commonFields.obj)
  }

  private def properties(common: Common, eventProps: JObject): JObject = {
    import org.json4s.JsonDSL._
    common.context map { context ⇒
      try {
        ("context" → Extraction.decompose(context)) ~ eventProps
      } catch {
        case e: Throwable ⇒
          throw new ConnectorException(
            s"Cannot convert $context to event JSON. ${e.getMessage }", e
          )
      }
    } getOrElse eventProps
  }

  private def commonToJson(common: Common): JObject =
    commonToJson(common, common.`type`)

  private def commonToJson(common: Common, typ: String): JObject = {
    import org.json4s.JsonDSL._
      common.userId.orElse(common.anonymousId) match {
        case Some(userId) ⇒
          ("event" → typ) ~
            ("entityType" → "user") ~
            ("entityId" → userId) ~
            ("eventTime" → common.timestamp)

        case None ⇒
          throw new ConnectorException(
            "there was no `userId` or `anonymousId` in the common fields."
          )
      }
  }
}

object Events {

  private[prediction] case class Track(
    event: String,
    properties: Option[JObject] = None
  )

  private[prediction] case class Alias(previousId: String, userId: String)

  private[prediction] case class Group(
    groupId: String,
    traits: Option[JObject] = None
  )

  private[prediction] case class Screen(
    name: String,
    properties: Option[JObject] = None
  )

  private[prediction] case class Page(
    name: String,
    properties: Option[JObject] = None
  )

  private[prediction] case class Identify(
    userId: String,
    traits: Option[JObject]
  )

}

object Common {

  private[prediction] case class Integrations(
    All: Boolean = false,
    Mixpanel: Boolean = false,
    Marketo: Boolean = false,
    Salesforse: Boolean = false
  )

  private[prediction] case class Context(
    app: App,
    campaign: Campaign,
    device: Device,
    ip: String,
    library: Library,
    locale: String,
    network: Network,
    location: Location,
    os: OS,
    referrer: Referrer,
    screen: Screen,
    timezone: String,
    userAgent: String
  )

  private[prediction] case class Screen(width: Int, height: Int, density: Int)

  private[prediction] case class Referrer(id: String, `type`: String)

  private[prediction] case class OS(name: String, version: String)

  private[prediction] case class Location(
    city: Option[String] = None,
    country: Option[String] = None,
    latitude: Option[Double] = None,
    longitude: Option[Double] = None,
    speed: Option[Int] = None
  )

  private[prediction] case class Network(
    bluetooth: Option[Boolean] = None,
    carrier: Option[String] = None,
    cellular: Option[Boolean] = None,
    wifi: Option[Boolean] = None
  )

  private[prediction] case class Library(name: String, version: String)

  private[prediction] case class Device(
    id: Option[String] = None,
    advertisingId: Option[String] = None,
    adTrackingEnabled: Option[Boolean] = None,
    manufacturer: Option[String] = None,
    model: Option[String] = None,
    name: Option[String] = None,
    `type`: Option[String] = None,
    token: Option[String] = None
  )

  private[prediction] case class Campaign(
    name: Option[String] = None,
    source: Option[String] = None,
    medium: Option[String] = None,
    term: Option[String] = None,
    content: Option[String] = None
  )

  private[prediction] case class App(
    name: Option[String] = None,
    version: Option[String] = None,
    build: Option[String] = None
  )

}

private[prediction] case class Common(
  `type`: String,
  sendAt: String,
  timestamp: String,
  anonymousId: Option[String] = None,
  userId: Option[String] = None,
  context: Option[Common.Context] = None,
  integrations: Option[Common.Integrations] = None
)
