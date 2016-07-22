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

package org.apache.predictionio.data.webhooks.segmentio

import org.apache.predictionio.data.webhooks.ConnectorTestUtil

import org.specs2.mutable._

class SegmentIOConnectorSpec extends Specification with ConnectorTestUtil {

  // TODO: test different optional fields

  val commonFields =
    s"""
       |  "anonymous_id": "id",
       |  "sent_at": "sendAt",
       |  "version": "2",
     """.stripMargin

  "SegmentIOConnector" should {

    "convert group with context to event JSON" in {
      val context =
        """
          |  "context": {
          |    "app": {
          |      "name": "InitechGlobal",
          |      "version": "545",
          |      "build": "3.0.1.545"
          |    },
          |    "campaign": {
          |      "name": "TPS Innovation Newsletter",
          |      "source": "Newsletter",
          |      "medium": "email",
          |      "term": "tps reports",
          |      "content": "image link"
          |    },
          |    "device": {
          |      "id": "B5372DB0-C21E-11E4-8DFC-AA07A5B093DB",
          |      "advertising_id": "7A3CBEA0-BDF5-11E4-8DFC-AA07A5B093DB",
          |      "ad_tracking_enabled": true,
          |      "manufacturer": "Apple",
          |      "model": "iPhone7,2",
          |      "name": "maguro",
          |      "type": "ios",
          |      "token": "ff15bc0c20c4aa6cd50854ff165fd265c838e5405bfeb9571066395b8c9da449"
          |    },
          |    "ip": "8.8.8.8",
          |    "library": {
          |      "name": "analytics-ios",
          |      "version": "1.8.0"
          |    },
          |    "network": {
          |      "bluetooth": false,
          |      "carrier": "T-Mobile NL",
          |      "cellular": true,
          |      "wifi": false
          |    },
          |    "location": {
          |      "city": "San Francisco",
          |      "country": "United States",
          |      "latitude": 40.2964197,
          |      "longitude": -76.9411617,
          |      "speed": 0
          |    },
          |    "os": {
          |      "name": "iPhone OS",
          |      "version": "8.1.3"
          |    },
          |    "referrer": {
          |      "id": "ABCD582CDEFFFF01919",
          |      "type": "dataxu"
          |    },
          |    "screen": {
          |      "width": 320,
          |      "height": 568,
          |      "density": 2
          |    },
          |    "timezone": "Europe/Amsterdam",
          |    "user_agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_5)"
          |  }
        """.stripMargin

      val group =
        s"""
           |{ $commonFields
            |  "type": "group",
            |  "group_id": "groupId",
            |  "user_id": "userIdValue",
            |  "timestamp" : "2012-12-02T00:30:08.276Z",
            |  "traits": {
            |    "name": "groupName",
            |    "employees": 329,
            |  },
            |  $context
            |}
        """.stripMargin

      val expected =
        s"""
          |{
          |  "event": "group",
          |  "entityType": "user",
          |  "entityId": "userIdValue",
          |  "properties": {
          |    $context,
          |    "group_id": "groupId",
          |    "traits": {
          |      "name": "groupName",
          |      "employees": 329
          |    },
          |  },
          |  "eventTime" : "2012-12-02T00:30:08.276Z"
          |}
        """.stripMargin

      check(SegmentIOConnector, group, expected)
    }

    "convert group to event JSON" in {
      val group =
        s"""
          |{ $commonFields
          |  "type": "group",
          |  "group_id": "groupId",
          |  "user_id": "userIdValue",
          |  "timestamp" : "2012-12-02T00:30:08.276Z",
          |  "traits": {
          |    "name": "groupName",
          |    "employees": 329,
          |  }
          |}
        """.stripMargin

      val expected =
        """
          |{
          |  "event": "group",
          |  "entityType": "user",
          |  "entityId": "userIdValue",
          |  "properties": {
          |    "group_id": "groupId",
          |    "traits": {
          |      "name": "groupName",
          |      "employees": 329
          |    }
          |  },
          |  "eventTime" : "2012-12-02T00:30:08.276Z"
          |}
        """.stripMargin

      check(SegmentIOConnector, group, expected)
    }

    "convert screen to event JSON" in {
      val screen =
        s"""
          |{ $commonFields
          |  "type": "screen",
          |  "name": "screenName",
          |  "user_id": "userIdValue",
          |  "timestamp" : "2012-12-02T00:30:08.276Z",
          |  "properties": {
          |    "variation": "screenVariation"
          |  }
          |}
        """.stripMargin

      val expected =
        """
          |{
          |  "event": "screen",
          |  "entityType": "user",
          |  "entityId": "userIdValue",
          |  "properties": {
          |    "properties": {
          |      "variation": "screenVariation"
          |    },
          |    "name": "screenName"
          |  },
          |  "eventTime" : "2012-12-02T00:30:08.276Z"
          |}
        """.stripMargin

      check(SegmentIOConnector, screen, expected)
    }

    "convert page to event JSON" in {
      val page =
       s"""
          |{ $commonFields
          |  "type": "page",
          |  "name": "pageName",
          |  "user_id": "userIdValue",
          |  "timestamp" : "2012-12-02T00:30:08.276Z",
          |  "properties": {
          |    "title": "pageTitle",
          |    "url": "pageUrl"
          |  }
          |}
        """.stripMargin

      val expected =
        """
          |{
          |  "event": "page",
          |  "entityType": "user",
          |  "entityId": "userIdValue",
          |  "properties": {
          |    "properties": {
          |      "title": "pageTitle",
          |      "url": "pageUrl"
          |    },
          |    "name": "pageName"
          |  },
          |  "eventTime" : "2012-12-02T00:30:08.276Z"
          |}
        """.stripMargin

      check(SegmentIOConnector, page, expected)
    }

    "convert alias to event JSON" in {
      val alias =
        s"""
          |{ $commonFields
          |  "type": "alias",
          |  "previous_id": "previousIdValue",
          |  "user_id": "userIdValue",
          |  "timestamp" : "2012-12-02T00:30:08.276Z"
          |}
        """.stripMargin

      val expected =
        """
          |{
          |  "event": "alias",
          |  "entityType": "user",
          |  "entityId": "userIdValue",
          |  "properties": {
          |    "previous_id" : "previousIdValue"
          |  },
          |  "eventTime" : "2012-12-02T00:30:08.276Z"
          |}
        """.stripMargin

      check(SegmentIOConnector, alias, expected)
    }

    "convert track to event JSON" in {
      val track =
       s"""
          |{ $commonFields
          |  "user_id": "some_user_id",
          |  "type": "track",
          |  "event": "Registered",
          |  "timestamp" : "2012-12-02T00:30:08.276Z",
          |  "properties": {
          |    "plan": "Pro Annual",
          |    "accountType" : "Facebook"
          |  }
          |}
        """.stripMargin

      val expected =
        """
          |{
          |  "event": "track",
          |  "entityType": "user",
          |  "entityId": "some_user_id",
          |  "properties": {
          |    "event": "Registered",
          |    "properties": {
          |      "plan": "Pro Annual",
          |      "accountType": "Facebook"
          |    }
          |  },
          |  "eventTime" : "2012-12-02T00:30:08.276Z"
          |}
        """.stripMargin

      check(SegmentIOConnector, track, expected)
    }

    "convert identify to event JSON" in {
      val identify = s"""
        { $commonFields
          "type"      : "identify",
          "user_id"    : "019mr8mf4r",
          "traits"    : {
              "email"            : "achilles@segment.com",
              "name"             : "Achilles",
              "subscription_plan" : "Premium",
              "friendCount"      : 29
          },
          "timestamp" : "2012-12-02T00:30:08.276Z"
        }
      """

      val expected = """
        {
          "event" : "identify",
          "entityType": "user",
          "entityId" : "019mr8mf4r",
          "properties" : {
            "traits" : {
              "email"            : "achilles@segment.com",
              "name"             : "Achilles",
              "subscription_plan" : "Premium",
              "friendCount"      : 29
            }
          },
          "eventTime" : "2012-12-02T00:30:08.276Z"
        }
      """

      check(SegmentIOConnector, identify, expected)
    }

  }

}
