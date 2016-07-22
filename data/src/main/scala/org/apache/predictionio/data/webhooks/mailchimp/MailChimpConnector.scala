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


package org.apache.predictionio.data.webhooks.mailchimp

import org.apache.predictionio.data.webhooks.FormConnector
import org.apache.predictionio.data.webhooks.ConnectorException
import org.apache.predictionio.data.storage.EventValidation
import org.apache.predictionio.data.Utils

import org.json4s.JObject

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

private[predictionio] object MailChimpConnector extends FormConnector {

  override
  def toEventJson(data: Map[String, String]): JObject = {

    val json = data.get("type") match {
      case Some("subscribe") => subscribeToEventJson(data)
      // UNSUBSCRIBE
      case Some("unsubscribe") => unsubscribeToEventJson(data)
      // PROFILE UPDATES
      case Some("profile") => profileToEventJson(data)
      // EMAIL UPDATE
      case Some("upemail") => upemailToEventJson(data)
      // CLEANED EMAILS
      case Some("cleaned") => cleanedToEventJson(data)
      // CAMPAIGN SENDING STATUS
      case Some("campaign") => campaignToEventJson(data)
      // invalid type
      case Some(x) => throw new ConnectorException(
        s"Cannot convert unknown MailChimp data type ${x} to event JSON")
      case None => throw new ConnectorException(
        s"The field 'type' is required for MailChimp data.")
    }
    json
  }


  val mailChimpDateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(EventValidation.defaultTimeZone)

  def parseMailChimpDateTime(s: String): DateTime = {
    mailChimpDateTimeFormat.parseDateTime(s)
  }

  def subscribeToEventJson(data: Map[String, String]): JObject = {

    import org.json4s.JsonDSL._

    /*
    "type": "subscribe",
    "fired_at": "2009-03-26 21:35:57",
    "data[id]": "8a25ff1d98",
    "data[list_id]": "a6b5da1054",
    "data[email]": "api@mailchimp.com",
    "data[email_type]": "html",
    "data[merges][EMAIL]": "api@mailchimp.com",
    "data[merges][FNAME]": "MailChimp",
    "data[merges][LNAME]": "API",
    "data[merges][INTERESTS]": "Group1,Group2",
    "data[ip_opt]": "10.20.10.30",
    "data[ip_signup]": "10.20.10.30"
    */

    // convert to ISO8601 format
    val eventTime = Utils.dateTimeToString(parseMailChimpDateTime(data("fired_at")))

    // TODO: handle optional fields
    val json =
      ("event" -> "subscribe") ~
      ("entityType" -> "user") ~
      ("entityId" -> data("data[id]")) ~
      ("targetEntityType" -> "list") ~
      ("targetEntityId" -> data("data[list_id]")) ~
      ("eventTime" -> eventTime) ~
      ("properties" -> (
        ("email" -> data("data[email]")) ~
        ("email_type" -> data("data[email_type]")) ~
        ("merges" -> (
          ("EMAIL" -> data("data[merges][EMAIL]")) ~
          ("FNAME" -> data("data[merges][FNAME]"))) ~
          ("LNAME" -> data("data[merges][LNAME]")) ~
          ("INTERESTS" -> data.get("data[merges][INTERESTS]"))
        )) ~
        ("ip_opt" -> data("data[ip_opt]")) ~
        ("ip_signup" -> data("data[ip_signup]")
      ))

    json

  }

  def unsubscribeToEventJson(data: Map[String, String]): JObject = {

    import org.json4s.JsonDSL._

    /*
    "action" will either be "unsub" or "delete".
    The reason will be "manual" unless caused by a spam complaint - then it will be "abuse"

    "type": "unsubscribe",
    "fired_at": "2009-03-26 21:40:57",
    "data[action]": "unsub",
    "data[reason]": "manual",
    "data[id]": "8a25ff1d98",
    "data[list_id]": "a6b5da1054",
    "data[email]": "api+unsub@mailchimp.com",
    "data[email_type]": "html",
    "data[merges][EMAIL]": "api+unsub@mailchimp.com",
    "data[merges][FNAME]": "MailChimp",
    "data[merges][LNAME]": "API",
    "data[merges][INTERESTS]": "Group1,Group2",
    "data[ip_opt]": "10.20.10.30",
    "data[campaign_id]": "cb398d21d2",
    */

    // convert to ISO8601 format
    val eventTime = Utils.dateTimeToString(parseMailChimpDateTime(data("fired_at")))

    val json =
      ("event" -> "unsubscribe") ~
      ("entityType" -> "user") ~
      ("entityId" -> data("data[id]")) ~
      ("targetEntityType" -> "list") ~
      ("targetEntityId" -> data("data[list_id]")) ~
      ("eventTime" -> eventTime) ~
      ("properties" -> (
        ("action" -> data("data[action]")) ~
        ("reason" -> data("data[reason]")) ~
        ("email" -> data("data[email]")) ~
        ("email_type" -> data("data[email_type]")) ~
        ("merges" -> (
          ("EMAIL" -> data("data[merges][EMAIL]")) ~
          ("FNAME" -> data("data[merges][FNAME]"))) ~
          ("LNAME" -> data("data[merges][LNAME]")) ~
          ("INTERESTS" -> data.get("data[merges][INTERESTS]"))
        )) ~
        ("ip_opt" -> data("data[ip_opt]")) ~
        ("campaign_id" -> data("data[campaign_id]")
      ))

    json

  }

  def profileToEventJson(data: Map[String, String]): JObject = {

    import org.json4s.JsonDSL._

    /*
    "type": "profile",
    "fired_at": "2009-03-26 21:31:21",
    "data[id]": "8a25ff1d98",
    "data[list_id]": "a6b5da1054",
    "data[email]": "api@mailchimp.com",
    "data[email_type]": "html",
    "data[merges][EMAIL]": "api@mailchimp.com",
    "data[merges][FNAME]": "MailChimp",
    "data[merges][LNAME]": "API",
    "data[merges][INTERESTS]": "Group1,Group2", \\OPTIONAL
    "data[ip_opt]": "10.20.10.30"
    */

    // convert to ISO8601 format
    val eventTime = Utils.dateTimeToString(parseMailChimpDateTime(data("fired_at")))

    val json =
      ("event" -> "profile") ~
      ("entityType" -> "user") ~
      ("entityId" -> data("data[id]")) ~
      ("targetEntityType" -> "list") ~
      ("targetEntityId" -> data("data[list_id]")) ~
      ("eventTime" -> eventTime) ~
      ("properties" -> (
        ("email" -> data("data[email]")) ~
        ("email_type" -> data("data[email_type]")) ~
        ("merges" -> (
          ("EMAIL" -> data("data[merges][EMAIL]")) ~
          ("FNAME" -> data("data[merges][FNAME]"))) ~
          ("LNAME" -> data("data[merges][LNAME]")) ~
          ("INTERESTS" -> data.get("data[merges][INTERESTS]"))
        )) ~
        ("ip_opt" -> data("data[ip_opt]")
      ))

    json

  }

  def upemailToEventJson(data: Map[String, String]): JObject = {

    import org.json4s.JsonDSL._

    /*
    "type": "upemail",
    "fired_at": "2009-03-26 22:15:09",
    "data[list_id]": "a6b5da1054",
    "data[new_id]": "51da8c3259",
    "data[new_email]": "api+new@mailchimp.com",
    "data[old_email]": "api+old@mailchimp.com"
    */

    // convert to ISO8601 format
    val eventTime = Utils.dateTimeToString(parseMailChimpDateTime(data("fired_at")))

    val json =
      ("event" -> "upemail") ~
      ("entityType" -> "user") ~
      ("entityId" -> data("data[new_id]")) ~
      ("targetEntityType" -> "list") ~
      ("targetEntityId" -> data("data[list_id]")) ~
      ("eventTime" -> eventTime) ~
      ("properties" -> (
        ("new_email" -> data("data[new_email]")) ~
        ("old_email" -> data("data[old_email]"))
      ))

    json

  }

  def cleanedToEventJson(data: Map[String, String]): JObject = {

    import org.json4s.JsonDSL._

    /*
    Reason will be one of "hard" (for hard bounces) or "abuse"
    "type": "cleaned",
    "fired_at": "2009-03-26 22:01:00",
    "data[list_id]": "a6b5da1054",
    "data[campaign_id]": "4fjk2ma9xd",
    "data[reason]": "hard",
    "data[email]": "api+cleaned@mailchimp.com"
    */

    // convert to ISO8601 format
    val eventTime = Utils.dateTimeToString(parseMailChimpDateTime(data("fired_at")))

    val json =
      ("event" -> "cleaned") ~
      ("entityType" -> "list") ~
      ("entityId" -> data("data[list_id]")) ~
      ("eventTime" -> eventTime) ~
      ("properties" -> (
        ("campaignId" -> data("data[campaign_id]")) ~
        ("reason" -> data("data[reason]")) ~
        ("email" -> data("data[email]"))
      ))

    json

  }

  def campaignToEventJson(data: Map[String, String]): JObject = {

    import org.json4s.JsonDSL._

    /*
    "type": "campaign",
    "fired_at": "2009-03-26 21:31:21",
    "data[id]": "5aa2102003",
    "data[subject]": "Test Campaign Subject",
    "data[status]": "sent",
    "data[reason]": "",
    "data[list_id]": "a6b5da1054"
    */

    // convert to ISO8601 format
    val eventTime = Utils.dateTimeToString(parseMailChimpDateTime(data("fired_at")))

    val json =
      ("event" -> "campaign") ~
      ("entityType" -> "campaign") ~
      ("entityId" -> data("data[id]")) ~
      ("targetEntityType" -> "list") ~
      ("targetEntityId" -> data("data[list_id]")) ~
      ("eventTime" -> eventTime) ~
      ("properties" -> (
        ("subject" -> data("data[subject]")) ~
        ("status" -> data("data[status]")) ~
        ("reason" -> data("data[reason]"))
      ))

    json

  }

}
