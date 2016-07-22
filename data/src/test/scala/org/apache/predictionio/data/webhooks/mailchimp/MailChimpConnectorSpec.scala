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

import org.apache.predictionio.data.webhooks.ConnectorTestUtil

import org.specs2.mutable._

class MailChimpConnectorSpec extends Specification with ConnectorTestUtil {

  // TOOD: test other events
  // TODO: test different optional fields

  "MailChimpConnector" should {

    "convert subscribe to event JSON" in {

      val subscribe = Map(
        "type" -> "subscribe",
        "fired_at" -> "2009-03-26 21:35:57",
        "data[id]" -> "8a25ff1d98",
        "data[list_id]" -> "a6b5da1054",
        "data[email]" -> "api@mailchimp.com",
        "data[email_type]" -> "html",
        "data[merges][EMAIL]" -> "api@mailchimp.com",
        "data[merges][FNAME]" -> "MailChimp",
        "data[merges][LNAME]" -> "API",
        "data[merges][INTERESTS]" -> "Group1,Group2", //optional
        "data[ip_opt]" -> "10.20.10.30",
        "data[ip_signup]" -> "10.20.10.30"
      )

      val expected = """
        {
          "event" : "subscribe",
          "entityType" : "user",
          "entityId" : "8a25ff1d98",
          "targetEntityType" : "list",
          "targetEntityId" : "a6b5da1054",
          "properties" : {
            "email" : "api@mailchimp.com",
            "email_type" : "html",
            "merges" : {
              "EMAIL" : "api@mailchimp.com",
              "FNAME" : "MailChimp",
              "LNAME" : "API"
              "INTERESTS" : "Group1,Group2"
            },
            "ip_opt" : "10.20.10.30",
            "ip_signup" : "10.20.10.30"
          },
          "eventTime" : "2009-03-26T21:35:57.000Z"
        }
      """

      check(MailChimpConnector, subscribe, expected)
    }

    //check unsubscribe to event Json
    "convert unsubscribe to event JSON" in {

      val unsubscribe = Map(
        "type" -> "unsubscribe",
        "fired_at" -> "2009-03-26 21:40:57",
        "data[action]" -> "unsub",
        "data[reason]" -> "manual",
        "data[id]" -> "8a25ff1d98",
        "data[list_id]" -> "a6b5da1054",
        "data[email]" -> "api+unsub@mailchimp.com",
        "data[email_type]" -> "html",
        "data[merges][EMAIL]" -> "api+unsub@mailchimp.com",
        "data[merges][FNAME]" -> "MailChimp",
        "data[merges][LNAME]" -> "API",
        "data[merges][INTERESTS]" -> "Group1,Group2", //optional 
        "data[ip_opt]" -> "10.20.10.30",
        "data[campaign_id]" -> "cb398d21d2"
      )

      val expected = """
        {
          "event" : "unsubscribe",
          "entityType" : "user",
          "entityId" : "8a25ff1d98",
          "targetEntityType" : "list",
          "targetEntityId" : "a6b5da1054",
          "properties" : {
            "action" : "unsub",
            "reason" : "manual",
            "email" : "api+unsub@mailchimp.com",
            "email_type" : "html",
            "merges" : {
              "EMAIL" : "api+unsub@mailchimp.com",
              "FNAME" : "MailChimp",
              "LNAME" : "API"
              "INTERESTS" : "Group1,Group2"
            },
            "ip_opt" : "10.20.10.30",
            "campaign_id" : "cb398d21d2"
          },
          "eventTime" : "2009-03-26T21:40:57.000Z"
        }
      """

      check(MailChimpConnector, unsubscribe, expected)
    }

    //check profile update to event Json 
    "convert profile update to event JSON" in {

      val profileUpdate = Map(
        "type" -> "profile",
        "fired_at" -> "2009-03-26 21:31:21",
        "data[id]" -> "8a25ff1d98",
        "data[list_id]" -> "a6b5da1054",
        "data[email]" -> "api@mailchimp.com",
        "data[email_type]" -> "html",
        "data[merges][EMAIL]" -> "api@mailchimp.com",
        "data[merges][FNAME]" -> "MailChimp",
        "data[merges][LNAME]" -> "API",
        "data[merges][INTERESTS]" -> "Group1,Group2", //optional
        "data[ip_opt]" -> "10.20.10.30"
      )

      val expected = """
        {
          "event" : "profile",
          "entityType" : "user",
          "entityId" : "8a25ff1d98",
          "targetEntityType" : "list",
          "targetEntityId" : "a6b5da1054",
          "properties" : {
            "email" : "api@mailchimp.com",
            "email_type" : "html",
            "merges" : {
              "EMAIL" : "api@mailchimp.com",
              "FNAME" : "MailChimp",
              "LNAME" : "API"
              "INTERESTS" : "Group1,Group2"
            },
            "ip_opt" : "10.20.10.30"
          },
          "eventTime" : "2009-03-26T21:31:21.000Z"
        }
      """

      check(MailChimpConnector, profileUpdate, expected)
    }

    //check email update to event Json 
    "convert email update to event JSON" in {

      val emailUpdate = Map(
        "type" -> "upemail",
        "fired_at" -> "2009-03-26 22:15:09",
        "data[list_id]" -> "a6b5da1054",
        "data[new_id]" -> "51da8c3259",
        "data[new_email]" -> "api+new@mailchimp.com",
        "data[old_email]" -> "api+old@mailchimp.com"
      )

      val expected = """
        {
          "event" : "upemail",
          "entityType" : "user",
          "entityId" : "51da8c3259",
          "targetEntityType" : "list",
          "targetEntityId" : "a6b5da1054",
          "properties" : {
            "new_email" : "api+new@mailchimp.com",
            "old_email" : "api+old@mailchimp.com"
          },
          "eventTime" : "2009-03-26T22:15:09.000Z"
        }
      """

      check(MailChimpConnector, emailUpdate, expected)
    }

    //check cleaned email to event Json 
    "convert cleaned email to event JSON" in {

      val cleanedEmail = Map(
        "type" -> "cleaned",
        "fired_at" -> "2009-03-26 22:01:00",
        "data[list_id]" -> "a6b5da1054",
        "data[campaign_id]" -> "4fjk2ma9xd",
        "data[reason]" -> "hard",
        "data[email]" -> "api+cleaned@mailchimp.com"
      )

      val expected = """
        {
          "event" : "cleaned",
          "entityType" : "list",
          "entityId" : "a6b5da1054",
          "properties" : {
            "campaignId" : "4fjk2ma9xd",
            "reason" : "hard",
            "email" : "api+cleaned@mailchimp.com"
          },
          "eventTime" : "2009-03-26T22:01:00.000Z"
        }
      """

      check(MailChimpConnector, cleanedEmail, expected)
    }

    //check campaign sending status to event Json 
    "convert campaign sending status to event JSON" in {

      val campaign = Map(
        "type" -> "campaign",
        "fired_at" -> "2009-03-26 22:15:09",
        "data[id]" -> "5aa2102003",
        "data[subject]" -> "Test Campaign Subject",
        "data[status]" -> "sent",
        "data[reason]" -> "",
        "data[list_id]" -> "a6b5da1054"
      )

      val expected = """
        {
          "event" : "campaign",
          "entityType" : "campaign",
          "entityId" : "5aa2102003",
          "targetEntityType" : "list",
          "targetEntityId" : "a6b5da1054",
          "properties" : {
            "subject" : "Test Campaign Subject",
            "status" : "sent",
            "reason" : ""
          },
          "eventTime" : "2009-03-26T22:15:09.000Z"
        }
      """

      check(MailChimpConnector, campaign, expected)
    }

  }
}
